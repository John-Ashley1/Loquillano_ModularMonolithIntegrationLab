package edu.cit.loquillano.shop.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OrderSummary {

    private Long orderId;
    private String status;
    private String reason;
    private LocalDateTime createdAt;
    private List<OrderItemSummary> items;

    public OrderSummary() {
    }

    public OrderSummary(Long orderId, String status, String reason,
                         LocalDateTime createdAt, List<OrderItemSummary> items) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.items = items;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<OrderItemSummary> getItems() {
        return items;
    }
}
