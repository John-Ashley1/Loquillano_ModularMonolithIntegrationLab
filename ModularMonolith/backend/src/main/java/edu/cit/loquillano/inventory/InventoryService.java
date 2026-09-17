package edu.cit.loquillano.inventory;

import java.util.List;

/**
 * Public contract for the Inventory module. This is the ONLY type from this
 * package that other modules (e.g. edu.cit.loquillano.shop) are allowed to
 * depend on. The concrete implementation (InventoryServiceImpl) is
 * package-private on purpose — see InventoryServiceImpl for why.
 */
public interface InventoryService {

    /**
     * Fetches the current inventory record for a product.
     *
     * @throws ProductNotFoundException if no such product exists
     */
    InventoryItem getItem(String productId);

    /**
     * Attempts to reserve (deduct) the given quantity from stock.
     *
     * @throws ProductNotFoundException   if no such product exists
     * @throws InsufficientStockException if quantity exceeds current stock
     */
    InventoryItem reserve(String productId, int quantity);

    /**
     * Returns the given quantity to stock. Used by order cancellation.
     *
     * @throws ProductNotFoundException if no such product exists
     */
    InventoryItem restock(String productId, int quantity);

    /**
     * Returns every product's current inventory record, for read endpoints.
     */
    List<InventoryItem> getAllItems();
}
