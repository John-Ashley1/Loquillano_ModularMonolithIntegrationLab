package edu.cit.loquillano.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Live inventory read endpoint. Lives in the inventory package, so it's
 * free to use InventoryService (or, for a simple read, the repository
 * directly) without crossing any module boundary.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @Value("${app.inventory.low-stock-threshold:5}")
    private int lowStockThreshold;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryItemDTO> listInventory() {
        return inventoryService.getAllItems().stream()
                .map(item -> new InventoryItemDTO(
                        item.getProductId(),
                        item.getName(),
                        item.getStock(),
                        item.getStock() < lowStockThreshold))
                .toList();
    }
}
