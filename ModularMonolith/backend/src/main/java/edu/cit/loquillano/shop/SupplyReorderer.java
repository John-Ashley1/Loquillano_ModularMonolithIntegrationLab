package edu.cit.loquillano.shop;

import edu.cit.loquillano.supplier.ReorderResult;
import edu.cit.loquillano.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntSupplier;

/**
 * The one place that places supplier reorders for the Order module. It is
 * ALWAYS called outside a database transaction, so a slow or failing
 * supplier can never keep stock rows locked. A per-product lock stops two
 * threads that notice the same shortage at the same moment from both
 * ordering.
 */
@Component
class SupplyReorderer {

    private static final Logger log = LoggerFactory.getLogger(SupplyReorderer.class);

    private final SupplierGateway supplierGateway;
    private final int reorderQuantity;
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    SupplyReorderer(SupplierGateway supplierGateway,
                    @Value("${app.inventory.reorder-quantity:20}") int reorderQuantity) {
        this.supplierGateway = supplierGateway;
        this.reorderQuantity = reorderQuantity;
    }

    /** Lab 3 auto-reorder: stock ran low. Skipped if something is already on its way or queued. */
    void reorderIfNothingComing(String productId, int stockLeft) {
        synchronized (lockFor(productId)) {
            if (supplierGateway.unitsOnOrder(productId) > 0 || supplierGateway.hasUnsubmittedReorder(productId)) {
                log.info("Low stock for {} ({} left) but a reorder is already on its way", productId, stockLeft);
                return;
            }
            ReorderResult result = supplierGateway.reorder(productId, reorderQuantity);
            log.info("Auto-reorder for {} (stock {}): {} - {}",
                    productId, stockLeft, result.getStatus(), result.getMessage());
        }
    }

    /**
     * A customer order needs more than we have or have coming. missingNow is
     * evaluated inside the lock, so a reorder another thread just placed is
     * taken into account.
     */
    void reorderShortage(String productId, int minimumUnits, IntSupplier missingNow) {
        synchronized (lockFor(productId)) {
            int missing = missingNow.getAsInt();
            if (missing <= 0 || supplierGateway.hasUnsubmittedReorder(productId)) {
                return;
            }
            int units = Math.max(minimumUnits > 0 ? minimumUnits : reorderQuantity, missing);
            ReorderResult result = supplierGateway.reorder(productId, units);
            log.info("Shortage reorder for {} ({} units): {} - {}",
                    productId, units, result.getStatus(), result.getMessage());
        }
    }

    private Object lockFor(String productId) {
        return locks.computeIfAbsent(productId, k -> new Object());
    }
}
