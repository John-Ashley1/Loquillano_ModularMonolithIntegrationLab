package edu.cit.loquillano.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Package-private would be too restrictive here since Spring Data needs to
 * generate a proxy, but this repository is never exposed outside the
 * inventory package directly — only through InventoryService.
 */
interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    /** SELECT ... FOR UPDATE, so two concurrent orders can never both take the last unit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") String id);
}
