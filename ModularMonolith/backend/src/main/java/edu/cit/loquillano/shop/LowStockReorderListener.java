package edu.cit.loquillano.shop;

import edu.cit.loquillano.event.LowStockEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lab 3 auto-reorder, run after the order that used the stock has committed
 * and on its own thread: placing an order never waits for LegacySupply.
 */
@Component
class LowStockReorderListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockReorderListener.class);

    private final SupplyReorderer reorderer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "low-stock-reorder");
        t.setDaemon(true);
        return t;
    });

    LowStockReorderListener(SupplyReorderer reorderer) {
        this.reorderer = reorderer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onLowStock(LowStockEvent event) {
        executor.execute(() -> {
            try {
                reorderer.reorderIfNothingComing(event.getProductId(), event.getRemainingStock());
            } catch (RuntimeException e) {
                log.warn("Auto-reorder for {} failed: {}", event.getProductId(), e.toString());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
