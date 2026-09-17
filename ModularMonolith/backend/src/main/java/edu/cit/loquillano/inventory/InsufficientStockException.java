package edu.cit.loquillano.inventory;

/**
 * Thrown when a reservation requests more units than are currently in stock.
 * Public: the Order module needs to catch this across the module boundary.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
