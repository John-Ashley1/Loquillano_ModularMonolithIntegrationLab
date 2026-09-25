package edu.cit.loquillano.event;

/**
 * Published by the supplier module when a purchase order it tracks
 * transitions to DELIVERED. Carries only our own domain data (product id,
 * units) — nothing about LegacySupply's PO numbers or status codes leaks
 * out through this event. Inventory listens for this and restocks; Order
 * and Inventory never call the supplier module directly for this flow.
 */
public class SupplierOrderDeliveredEvent {

    private final String productId;
    private final int units;

    public SupplierOrderDeliveredEvent(String productId, int units) {
        this.productId = productId;
        this.units = units;
    }

    public String getProductId() {
        return productId;
    }

    public int getUnits() {
        return units;
    }
}
