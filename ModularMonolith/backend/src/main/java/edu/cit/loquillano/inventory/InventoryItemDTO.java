package edu.cit.loquillano.inventory;

public class InventoryItemDTO {

    private final String productId;
    private final String name;
    private final int stock;
    private final boolean lowStock;

    public InventoryItemDTO(String productId, String name, int stock, boolean lowStock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
        this.lowStock = lowStock;
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

    public boolean isLowStock() {
        return lowStock;
    }
}
