package edu.cit.loquillano.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Package-private on purpose: this class is an implementation detail of the
 * Inventory module. No other module (e.g. edu.cit.loquillano.shop) can even
 * reference this class name at compile time — they can only see and inject
 * the public InventoryService interface. Spring can still discover and wire
 * this bean at runtime via component scanning/reflection, so the boundary
 * costs nothing operationally, only compile-time coupling.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "No product found with id " + productId));
    }

    @Override
    @Transactional
    public InventoryItem reserve(String productId, int quantity) {
        InventoryItem item = getItem(productId);
        if (quantity > item.getStock()) {
            throw new InsufficientStockException(
                    "Requested quantity " + quantity + " exceeds available stock "
                            + item.getStock() + " for product " + productId);
        }
        item.setStock(item.getStock() - quantity);
        return inventoryRepository.save(item);
    }

    @Override
    @Transactional
    public InventoryItem restock(String productId, int quantity) {
        InventoryItem item = getItem(productId);
        item.setStock(item.getStock() + quantity);
        return inventoryRepository.save(item);
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}
