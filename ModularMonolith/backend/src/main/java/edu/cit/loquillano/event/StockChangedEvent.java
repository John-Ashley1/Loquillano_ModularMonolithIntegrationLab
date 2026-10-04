package edu.cit.loquillano.event;

/**
 * Published by Inventory whenever a product's stock changes for ANY reason
 * (order reserved, order cancelled, supplier delivery...). Carries plain
 * data only. Whoever cares (the sales channel, backorder handling) listens;
 * Inventory knows nothing about them.
 */
public class StockChangedEvent {

    private final String productId;
    private final int available;
    private final int delta;

    public StockChangedEvent(String productId, int available, int delta) {
        this.productId = productId;
        this.available = available;
        this.delta = delta;
    }

    public String getProductId() {
        return productId;
    }

    /** Stock on hand after the change. */
    public int getAvailable() {
        return available;
    }

    /** Positive for a restock, negative for a reservation. */
    public int getDelta() {
        return delta;
    }
}
