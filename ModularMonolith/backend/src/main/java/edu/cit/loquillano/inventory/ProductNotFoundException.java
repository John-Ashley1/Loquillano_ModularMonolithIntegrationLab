package edu.cit.loquillano.inventory;

/**
 * Thrown when a productId does not exist in the inventory table.
 * Public: the Order module needs to catch this across the module boundary.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}
