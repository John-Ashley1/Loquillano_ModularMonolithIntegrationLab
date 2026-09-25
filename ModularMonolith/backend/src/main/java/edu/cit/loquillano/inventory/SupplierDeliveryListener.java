package edu.cit.loquillano.inventory;

import edu.cit.loquillano.event.SupplierOrderDeliveredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Restocks inventory when the supplier module reports a delivered order.
 * Package-private and imports only from edu.cit.loquillano.event — the
 * Inventory module never calls the supplier module directly for this;
 * the supplier module never calls InventoryService directly either. Both
 * sides only know about the event.
 */
@Component
class SupplierDeliveryListener {

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        inventoryService.restock(event.getProductId(), event.getUnits());
    }
}
