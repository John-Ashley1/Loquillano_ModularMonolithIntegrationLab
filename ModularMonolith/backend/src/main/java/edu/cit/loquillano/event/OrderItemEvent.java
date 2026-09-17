package edu.cit.loquillano.event;

/**
 * Simple line-item payload carried inside OrderPlacedEvent. Intentionally
 * has no dependency on any JPA entity — events should carry plain data,
 * not persistence objects, so modules that only see the event package
 * never need the Order or Inventory module's internal types.
 */
public class OrderItemEvent {

    private final String productId;
    private final int quantity;

    public OrderItemEvent(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
