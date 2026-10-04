package edu.cit.loquillano.channel;

import edu.cit.loquillano.shop.OrderService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task 6, sequencing. Tiangge wants to see, in this order:
 *   1. the new (higher) stock after a delivery,
 *   2. the backorder resolved as ACCEPTED,
 *   3. the lower stock that reflects the order.
 * So backorders are only resolved AFTER the restock has been published, one
 * at a time, and each resolution is followed by a stock publish before the
 * next backorder is touched.
 */
@Component
class BackorderCoordinator {

    private static final Logger log = LoggerFactory.getLogger(BackorderCoordinator.class);

    private final OrderService orderService;
    private final StockPublisher stockPublisher;
    private final ChannelState state;
    private final AtomicBoolean queued = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tiangge-backorders");
        t.setDaemon(true);
        return t;
    });

    BackorderCoordinator(OrderService orderService, StockPublisher stockPublisher, ChannelState state) {
        this.orderService = orderService;
        this.stockPublisher = stockPublisher;
        this.state = state;
    }

    @EventListener
    void onRestockPublished(RestockPublished event) {
        if (queued.compareAndSet(false, true)) {
            executor.execute(this::run);
        }
    }

    private void run() {
        queued.set(false);
        if (!state.isLive()) {
            return;
        }
        try {
            // The resolution is sent to Tiangge from inside resolveNextBackorder (see
            // BackorderResolutionListener); then we wait for the lowered stock to go out.
            while (orderService.resolveNextBackorder()) {
                if (!stockPublisher.awaitPublished(25_000)) {
                    log.warn("Stock publish after a backorder resolution is taking long; continuing");
                }
            }
        } catch (RuntimeException e) {
            log.warn("Backorder resolution run failed: {}", e.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
