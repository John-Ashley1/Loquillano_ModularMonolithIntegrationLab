package edu.cit.loquillano.channel;

import edu.cit.loquillano.event.BackorderResolvedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Task 6, channel side: when the Order module resolves a backorder, tell
 * Tiangge. Two steps:
 *  - BEFORE the order's transaction commits, note "resolution owed" in the
 *    SAME transaction. The stock decrease and the debt to Tiangge therefore
 *    become visible together, so the stock publisher cannot sneak the lower
 *    stock out ahead of the resolution.
 *  - AFTER the commit, send it.
 * If the order is not one of ours from Tiangge nothing is found and nothing
 * happens.
 */
@Component
class BackorderResolutionListener {

    private static final Logger log = LoggerFactory.getLogger(BackorderResolutionListener.class);

    private final ChannelOrderRepository orders;
    private final OutboxSender outbox;

    BackorderResolutionListener(ChannelOrderRepository orders, OutboxSender outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void flagResolution(BackorderResolvedEvent event) {
        orders.findByShopOrderId(event.getOrderId()).ifPresent(c ->
                orders.markResolutionInTx(c.getTianggeOrderId(), event.isAccepted() ? "ACCEPTED" : "CANCELLED"));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void sendResolution(BackorderResolvedEvent event) {
        try {
            orders.findByShopOrderId(event.getOrderId()).ifPresent(c -> {
                log.info("Shop order {} (Tiangge order {}) backorder resolved: {}",
                        event.getOrderId(), c.getTianggeOrderId(), event.isAccepted() ? "ACCEPTED" : "CANCELLED");
                outbox.dispatch(c.getTianggeOrderId());
            });
        } catch (RuntimeException e) {
            // the flagged row is picked up by the outbox sweep
            log.warn("Could not report backorder resolution for shop order {}: {}", event.getOrderId(), e.toString());
        }
    }
}
