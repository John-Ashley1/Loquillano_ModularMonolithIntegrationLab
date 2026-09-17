package edu.cit.loquillano.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private would be too restrictive here since Spring Data needs to
 * generate a proxy, but this repository is never exposed outside the
 * inventory package directly — only through InventoryService.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {
}
