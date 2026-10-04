package edu.cit.loquillano.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers what we owe Tiangge: decisions, backorder resolutions and
 * cancellation confirmations. The fast path is a direct call right after
 * the work is committed; the database row (ChannelOrder) is the durable
 * to-do list, so anything that failed (Tiangge slow, 503, app restarted)
 * is retried by the sweep below until it goes through. Every message is
 * idempotent on Tiangge's side, so repeating one is always safe.
 */
@Component
class OutboxSender {

    private static final Logger log = LoggerFactory.getLogger(OutboxSender.class);

    private final TianggeClient client;
    private final ChannelOrderRepository orders;
    private final ChannelState state;
    private final StockPublisher stockPublisher;
    private final TianggeWriteGate gate;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    OutboxSender(TianggeClient client, ChannelOrderRepository orders, ChannelState state,
                 StockPublisher stockPublisher, TianggeWriteGate gate) {
        this.client = client;
        this.orders = orders;
        this.state = state;
        this.stockPublisher = stockPublisher;
        this.gate = gate;
    }

    /** Sends whatever is still owed for one Tiangge order, in order: decision, resolution, cancellation. */
    void dispatch(String tianggeOrderId) {
        if (!state.isLive()) {
            return;
        }
        // One sender per order at a time: the fast path and the retry sweep must never
        // post the same message twice in parallel. Skipped work is picked up by the sweep.
        if (!inFlight.add(tianggeOrderId)) {
            return;
        }
        // Shared side of the gate: many messages may be in flight together, but a stock update
        // cannot start while any is, and every message is recorded in the database before
        // the gate opens again.
        gate.decisions().lock();
        try {
            doDispatch(tianggeOrderId);
        } finally {
            gate.decisions().unlock();
            inFlight.remove(tianggeOrderId);
        }
    }

    private void doDispatch(String tianggeOrderId) {
        ChannelOrder c = orders.findById(tianggeOrderId).orElse(null);
        if (c == null) {
            return;
        }

        if (!c.isDecisionSent()) {
            try {
                client.postDecision(tianggeOrderId, c.getDecision(), String.valueOf(c.getShopOrderId()), 2);
                orders.markDecisionSent(tianggeOrderId);
                log.info("Decision {} sent for Tiangge order {} (shop order {})",
                        c.getDecision(), tianggeOrderId, c.getShopOrderId());
                if ("ACCEPTED".equals(c.getDecision())) {
                    stockPublisher.markDirty(); // Tiangge expects a stock update AFTER an accepted order
                }
            } catch (TianggeException e) {
                if (!settled(e, "decision", tianggeOrderId)) {
                    return;
                }
                orders.markDecisionSent(tianggeOrderId);
            }
        }

        if (c.getResolution() != null && !c.isResolutionSent()) {
            try {
                client.postResolution(tianggeOrderId, c.getResolution(), 2);
                orders.markResolutionSent(tianggeOrderId);
                log.info("Backorder of Tiangge order {} resolved as {}", tianggeOrderId, c.getResolution());
                if ("ACCEPTED".equals(c.getResolution())) {
                    stockPublisher.markDirty();
                }
            } catch (TianggeException e) {
                if (!settled(e, "resolution", tianggeOrderId)) {
                    return;
                }
                orders.markResolutionSent(tianggeOrderId);
            }
        }

        if (c.isCancelRequested() && !c.isCancelConfirmed()) {
            try {
                client.postCancellation(tianggeOrderId, true, 2);
                orders.markCancelConfirmed(tianggeOrderId);
                log.info("Cancellation of Tiangge order {} confirmed (restocked)", tianggeOrderId);
                stockPublisher.markDirty();
            } catch (TianggeException e) {
                if (!settled(e, "cancellation", tianggeOrderId)) {
                    return;
                }
                orders.markCancelConfirmed(tianggeOrderId);
            }
        }
    }

    /**
     * True if the error means "Tiangge already has this / will never accept
     * it" (404, 409), so we stop retrying. Anything else stays queued.
     */
    private boolean settled(TianggeException e, String what, String orderId) {
        if (e.getStatus() == 404 || e.getStatus() == 409) {
            log.warn("Tiangge says the {} for order {} is already settled ({}); not retrying", what, orderId, e);
            return true;
        }
        log.warn("Could not send {} for Tiangge order {} yet, will retry: {}", what, orderId, e.toString());
        return false;
    }

    /** Safety net: retries anything still owed. Skips decisions that the fast path is probably still sending. */
    @Scheduled(fixedDelay = 5_000, initialDelay = 10_000)
    void retryOutstanding() {
        if (!state.isLive()) {
            return;
        }
        LocalDateTime recent = LocalDateTime.now().minusSeconds(8);
        try {
            for (ChannelOrder c : orders.findOutstanding()) {
                if (!c.isDecisionSent() && c.getCreatedAt().isAfter(recent)) {
                    continue;
                }
                dispatch(c.getTianggeOrderId());
            }
        } catch (RuntimeException e) {
            log.warn("Outbox sweep failed: {}", e.toString());
        }
    }
}
