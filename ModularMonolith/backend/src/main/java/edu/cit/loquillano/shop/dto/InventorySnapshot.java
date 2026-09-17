package edu.cit.loquillano.shop.dto;

public class InventorySnapshot {

    private String productId;
    private String name;
    private int stock;

    public InventorySnapshot() {
    }

    public InventorySnapshot(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }
}
