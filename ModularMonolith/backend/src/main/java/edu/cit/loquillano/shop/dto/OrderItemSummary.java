package edu.cit.loquillano.shop.dto;

public class OrderItemSummary {

    private String productId;
    private int quantity;

    public OrderItemSummary() {
    }

    public OrderItemSummary(String productId, int quantity) {
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
