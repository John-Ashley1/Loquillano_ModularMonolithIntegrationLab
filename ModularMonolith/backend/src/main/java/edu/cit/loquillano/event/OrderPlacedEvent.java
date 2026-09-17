package edu.cit.loquillano.event;

import java.util.List;

/**
 * Published by the Order module when an order is CONFIRMED. This is the
 * only thing the Notification module is allowed to depend on from the
 * Order side — it never calls OrderService directly.
 */
public class OrderPlacedEvent {

    private final Long orderId;
    private final List<OrderItemEvent> items;

    public OrderPlacedEvent(Long orderId, List<OrderItemEvent> items) {
        this.orderId = orderId;
        this.items = items;
    }

    public Long getOrderId() {
        return orderId;
    }

    public List<OrderItemEvent> getItems() {
        return items;
    }
}
