package edu.cit.loquillano.shop.dto;

public class ItemOutcome {

    private String productId;
    private String outcome;

    public ItemOutcome() {
    }

    public ItemOutcome(String productId, String outcome) {
        this.productId = productId;
        this.outcome = outcome;
    }

    public String getProductId() {
        return productId;
    }

    public String getOutcome() {
        return outcome;
    }
}
