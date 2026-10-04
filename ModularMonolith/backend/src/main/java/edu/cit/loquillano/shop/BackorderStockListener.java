package edu.cit.loquillano.shop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Slow safety sweep for backorders: catches anything stuck (a purchase
 * order that failed, a restart in the middle) and cancels backorders that
 * can never be filled. The normal, prompt path is driven by whoever sells
 * the stock calling OrderService.resolveNextBackorder() after new stock has
 * been announced.
 */
@Component
class BackorderStockListener {

    private static final Logger log = LoggerFactory.getLogger(BackorderStockListener.class);

    private final OrderService orderService;

    BackorderStockListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${app.order.backorder-sweep-ms:30000}", initialDelay = 20000)
    void sweep() {
        try {
            orderService.reevaluateBackorders();
        } catch (RuntimeException e) {
            log.warn("Backorder sweep failed: {}", e.toString());
        }
    }
}
