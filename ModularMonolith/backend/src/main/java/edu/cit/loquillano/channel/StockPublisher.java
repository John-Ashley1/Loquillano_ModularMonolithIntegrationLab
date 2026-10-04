package edu.cit.loquillano.channel;

import edu.cit.loquillano.event.StockChangedEvent;
import edu.cit.loquillano.inventory.InventoryItem;
import edu.cit.loquillano.inventory.InventoryService;
import edu.cit.loquillano.shop.OrderService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task 3: Tiangge learns the new stock number whenever Inventory's stock
 * changes. It is driven ONLY by StockChangedEvent (published by Inventory
 * for every change, whatever caused it); there is no timer that publishes
 * on its own. A change schedules one short, coalesced flush that sends the
 * current stock of every listed product.
 *
 * Tiangge wants stock AFTER it has our decision, so a flush waits while any
 * decision / resolution / cancellation confirmation is still owed (for at
 * most MAX_HOLD_MS, so a Tiangge outage can never make stock go stale for
 * long). A failed publish is retried until it succeeds.
 */
@Component
class StockPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockPublisher.class);

    private static final long COALESCE_MS = 100;
    private static final long HOLD_POLL_MS = 400;
    private static final long RETRY_MS = 2_000;
    private static final long MAX_HOLD_MS = 20_000;

    private final InventoryService inventoryService;
    private final TianggeClient client;
    private final ChannelState state;
    private final ChannelOrderRepository channelOrders;
    private final OrderService orderService;
    private final TianggeWriteGate gate;
    private final ApplicationEventPublisher events;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "tiangge-stock-publisher");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private long generation;
    private long published;
    private long dirtySince;
    private boolean scheduled;
    private boolean restockPending;

    StockPublisher(InventoryService inventoryService, TianggeClient client,
                   ChannelState state, ChannelOrderRepository channelOrders,
                   OrderService orderService, TianggeWriteGate gate, ApplicationEventPublisher events) {
        this.inventoryService = inventoryService;
        this.client = client;
        this.state = state;
        this.channelOrders = channelOrders;
        this.orderService = orderService;
        this.gate = gate;
        this.events = events;
    }

    /** Runs after the change has committed, so we always read the committed number. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStockChanged(StockChangedEvent event) {
        if (event.getDelta() > 0) {
            synchronized (lock) {
                restockPending = true;
            }
        }
        if (state.isListed(event.getProductId())) {
            markDirty();
        } else if (!state.isLive()) {
            markDirty(); // not live yet: the first flush after going live covers it anyway
        }
    }

    void markDirty() {
        synchronized (lock) {
            generation++;
            if (dirtySince == 0) {
                dirtySince = System.currentTimeMillis();
            }
            if (!scheduled) {
                scheduled = true;
                executor.schedule(this::flush, COALESCE_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void flush() {
        long gen;
        synchronized (lock) {
            gen = generation;
        }
        try {
            if (!state.isLive()) {
                // The hold-for-decisions window must start when we go live, not when the change happened.
                synchronized (lock) {
                    dirtySince = System.currentTimeMillis();
                }
                retry(RETRY_MS);
                return;
            }
            List<TianggeClient.StockLevel> levels;
            // Exclusive: no decision is in flight while we work out the numbers and send them, so the
            // numbers match exactly the decisions Tiangge has received.
            gate.stock().lock();
            try {
                levels = currentLevels();
                client.putStock(levels);
            } finally {
                gate.stock().unlock();
            }
            log.info("Published stock to Tiangge: {}", levels);

            boolean announceRestock;
            synchronized (lock) {
                published = Math.max(published, gen);
                announceRestock = restockPending;
                restockPending = false;
                if (generation == gen) {
                    dirtySince = 0;
                    scheduled = false;
                } else {
                    // more changes landed while we were sending: they get a fresh hold window,
                    // otherwise the old (expired) one would let this flush skip the wait for decisions
                    dirtySince = System.currentTimeMillis();
                    executor.schedule(this::flush, 0, TimeUnit.MILLISECONDS);
                }
                lock.notifyAll();
            }
            if (announceRestock) {
                events.publishEvent(new RestockPublished());
            }
        } catch (RuntimeException e) {
            log.warn("Stock publish failed, retrying in {}ms: {}", RETRY_MS, e.toString());
            retry(RETRY_MS);
        }
    }

    /**
     * Blocks until every stock change made so far has been published to
     * Tiangge (or the timeout passes). Never call this from the publisher's own thread.
     */
    boolean awaitPublished(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            long target = generation;
            while (published < target) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The stock Tiangge is entitled to see right now. Tiangge keeps its own books from what we
     * TELL it (decisions, cancellations) and from supplier deliveries, so the number we publish
     * must follow the same rules, not just mirror the database:
     *  - an order we already accepted locally but have not yet told Tiangge about still counts
     *    as in stock (its decrease is published after the decision, never before);
     *  - a cancellation we already restocked locally but have not yet confirmed still counts as taken.
     * New stock (deliveries) is published at once, with no waiting.
     */
    private List<TianggeClient.StockLevel> currentLevels() {
        // Read the stock FIRST, then who is unannounced: an order that commits in between is added
        // back needlessly (published a little high, corrected right after its decision), never missed.
        List<InventoryItem> items = inventoryService.getAllItems();
        Map<String, Integer> adjust = pendingAdjustments();

        List<TianggeClient.StockLevel> levels = new ArrayList<>();
        for (InventoryItem item : items) {
            if (state.isListed(item.getProductId())) {
                int shown = item.getStock() + adjust.getOrDefault(item.getProductId(), 0);
                levels.add(new TianggeClient.StockLevel(item.getProductId(), Math.max(0, shown)));
            }
        }
        return levels;
    }

    private Map<String, Integer> pendingAdjustments() {
        Map<String, Integer> adjust = new HashMap<>();
        List<Long> accepted = channelOrders.findUnannouncedAccepted().stream()
                .map(ChannelOrder::getShopOrderId).toList();
        orderService.unitsInOrders(accepted).forEach((product, units) -> adjust.merge(product, units, Integer::sum));

        List<Long> cancelled = channelOrders.findUnconfirmedCancelsHoldingStock().stream()
                .map(ChannelOrder::getShopOrderId).toList();
        orderService.unitsInOrders(cancelled).forEach((product, units) -> adjust.merge(product, -units, Integer::sum));
        return adjust;
    }

    private void retry(long delayMs) {
        executor.schedule(this::flush, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
