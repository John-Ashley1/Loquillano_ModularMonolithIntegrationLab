package edu.cit.loquillano.event;

/**
 * Published by the Order module when an order is REJECTED (any line item
 * failed validation, so nothing was reserved).
 */
public class OrderRejectedEvent {

    private final Long orderId;
    private final String reason;

    public OrderRejectedEvent(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }
}
