package edu.cit.loquillano.inventory;

import edu.cit.loquillano.event.StockChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
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
 *
 * Lab 4: every stock change now publishes a StockChangedEvent, and rows are
 * locked while they are changed so concurrent orders cannot oversell.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "No product found with id " + productId));
    }

    // Deliberately NOT @Transactional: a "not found" must not mark the
    // caller's transaction rollback-only. The caller supplies the transaction.
    @Override
    public InventoryItem getItemForUpdate(String productId) {
        return inventoryRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "No product found with id " + productId));
    }

    @Override
    @Transactional
    public InventoryItem reserve(String productId, int quantity) {
        InventoryItem item = getItemForUpdate(productId);
        if (quantity > item.getStock()) {
            throw new InsufficientStockException(
                    "Requested quantity " + quantity + " exceeds available stock "
                            + item.getStock() + " for product " + productId);
        }
        item.setStock(item.getStock() - quantity);
        InventoryItem saved = inventoryRepository.save(item);
        eventPublisher.publishEvent(new StockChangedEvent(productId, saved.getStock(), -quantity));
        return saved;
    }

    @Override
    @Transactional
    public InventoryItem restock(String productId, int quantity) {
        InventoryItem item = getItemForUpdate(productId);
        item.setStock(item.getStock() + quantity);
        InventoryItem saved = inventoryRepository.save(item);
        eventPublisher.publishEvent(new StockChangedEvent(productId, saved.getStock(), quantity));
        return saved;
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}
