package edu.cit.loquillano.event;

/**
 * Published after any successful reserve() (including each line item of a
 * multi-item order) leaves a product's stock below the configured
 * low-stock threshold. Distinct from OrderPlaced/OrderRejected so
 * Notification can log it as a separate "reorder needed" entry.
 */
public class LowStockEvent {

    private final String productId;
    private final String productName;
    private final int remainingStock;

    public LowStockEvent(String productId, String productName, int remainingStock) {
        this.productId = productId;
        this.productName = productName;
        this.remainingStock = remainingStock;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getRemainingStock() {
        return remainingStock;
    }
}
