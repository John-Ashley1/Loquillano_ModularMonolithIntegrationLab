package edu.cit.loquillano.shop.dto;

import java.util.List;

public class OrderResponse {

    private Long orderId;
    private String status;
    private String reason;
    private List<ItemOutcome> items;
    private List<InventorySnapshot> inventory;

    public OrderResponse() {
    }

    public OrderResponse(Long orderId, String status, String reason,
                          List<ItemOutcome> items, List<InventorySnapshot> inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items;
        this.inventory = inventory;
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

    public List<ItemOutcome> getItems() {
        return items;
    }

    public List<InventorySnapshot> getInventory() {
        return inventory;
    }
}
