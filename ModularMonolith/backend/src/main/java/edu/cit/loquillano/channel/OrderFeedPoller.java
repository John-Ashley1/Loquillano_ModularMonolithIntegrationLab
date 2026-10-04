package edu.cit.loquillano.channel;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tasks 4 and 5: nobody tells the app an order arrived, so it asks. Every
 * few seconds it reads the feed from the stored cursor, handles each new
 * event, and only then moves the cursor forward. A batch is worked on by a
 * few threads at once (a flash sale must still be decided within 60
 * seconds) but all events of ONE Tiangge order stay in order on one thread,
 * so a cancellation can never overtake the order it cancels.
 *
 * Work that is slow is NEVER cancelled or interrupted (that used to leave
 * half-finished work behind and let the next poll start the same order a
 * second time). Instead an order that is still being worked on is simply
 * skipped by later polls until its thread finishes.
 */
@Component
class OrderFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(OrderFeedPoller.class);

    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES_PER_TICK = 20;
    private static final int MAX_ATTEMPTS_PER_EVENT = 12;
    private static final long BATCH_WAIT_MS = 40_000;

    private final TianggeClient client;
    private final ChannelState state;
    private final FeedCursorStore cursorStore;
    private final FeedEventProcessor processor;
    private final OutboxSender outbox;

    private final AtomicInteger threadNumber = new AtomicInteger();
    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "tiangge-feed-worker-" + threadNumber.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();

    OrderFeedPoller(TianggeClient client, ChannelState state, FeedCursorStore cursorStore,
                    FeedEventProcessor processor, OutboxSender outbox) {
        this.client = client;
        this.state = state;
        this.cursorStore = cursorStore;
        this.processor = processor;
        this.outbox = outbox;
    }

    @Scheduled(fixedDelayString = "${app.channel.poll-interval-ms:3000}", initialDelay = 5_000)
    void poll() {
        if (!state.isLive()) {
            return;
        }
        try {
            for (int page = 0; page < MAX_PAGES_PER_TICK; page++) {
                long cursor = cursorStore.get();
                TianggeClient.FeedPage feed;
                try {
                    feed = client.getFeed(cursor, PAGE_SIZE);
                } catch (TianggeException e) {
                    log.warn("Could not read the order feed (after={}), will try again: {}", cursor, e.toString());
                    return;
                }

                if (!feed.events().isEmpty()) {
                    log.info("Feed: {} event(s) after cursor {}", feed.events().size(), cursor);
                    if (!processBatch(feed.events())) {
                        return; // something is unfinished: keep the cursor, the batch is retried next tick
                    }
                }
                cursorStore.save(feed.nextCursor());

                if (feed.events().size() < PAGE_SIZE) {
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.error("Feed poll failed", e);
        }
    }

    /** @return true if every event in the batch was handled (or deliberately skipped). */
    private boolean processBatch(List<TianggeClient.FeedEvent> events) {
        Map<String, List<TianggeClient.FeedEvent>> byOrder = new LinkedHashMap<>();
        for (TianggeClient.FeedEvent e : events) {
            byOrder.computeIfAbsent(e.orderId(), k -> new ArrayList<>()).add(e);
        }

        boolean allOk = true;
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<String, List<TianggeClient.FeedEvent>> entry : byOrder.entrySet()) {
            String orderId = entry.getKey();
            List<TianggeClient.FeedEvent> group = entry.getValue();
            if (!inProgress.add(orderId)) {
                allOk = false; // an earlier poll is still working on this order
                continue;
            }
            futures.add(workers.submit(() -> {
                try {
                    return processGroup(group);
                } finally {
                    inProgress.remove(orderId);
                }
            }));
        }

        long deadline = System.currentTimeMillis() + BATCH_WAIT_MS;
        for (Future<Boolean> future : futures) {
            try {
                long remaining = Math.max(1, deadline - System.currentTimeMillis());
                allOk &= future.get(remaining, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                allOk = false; // keep running; later polls skip it while it is in progress
            } catch (ExecutionException e) {
                allOk = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return allOk;
    }

    private boolean processGroup(List<TianggeClient.FeedEvent> group) {
        for (TianggeClient.FeedEvent event : group) {
            String sendOrderId;
            try {
                processor.prepare(event);                 // supplier call, outside any transaction
                sendOrderId = processor.process(event);   // database work, one short transaction
                failures.remove(event.eventId());
            } catch (RuntimeException ex) {
                // A busy database (timeout, lock) is not the event's fault: do not count it.
                int count = (ex instanceof TransientDataAccessException)
                        ? failures.getOrDefault(event.eventId(), 0)
                        : failures.merge(event.eventId(), 1, Integer::sum);
                if (count >= MAX_ATTEMPTS_PER_EVENT) {
                    log.error("Giving up on feed event {} ({} order {}) after {} failed attempts: {}",
                            event.eventId(), event.type(), event.orderId(), count, ex.toString());
                    failures.remove(event.eventId());
                    continue; // skip it so one bad event cannot block the whole feed forever
                }
                log.warn("Feed event {} ({} order {}) failed, attempt {}: {}",
                        event.eventId(), event.type(), event.orderId(), count, ex.toString());
                return false;
            }

            if (sendOrderId != null) {
                try {
                    outbox.dispatch(sendOrderId); // the outbox sweep retries if this fails
                } catch (RuntimeException ex) {
                    log.warn("Sending to Tiangge for order {} failed, will retry: {}", sendOrderId, ex.toString());
                }
            }
        }
        return true;
    }

    @PreDestroy
    void shutdown() {
        workers.shutdown();
    }
}
