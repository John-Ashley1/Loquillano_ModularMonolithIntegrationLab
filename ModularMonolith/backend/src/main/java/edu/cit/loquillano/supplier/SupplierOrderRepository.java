package edu.cit.loquillano.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {
    List<SupplierOrder> findAllByStatus(SupplierOrderStatus status);
    List<SupplierOrder> findAllByStatusIn(List<SupplierOrderStatus> statuses);
    Optional<SupplierOrder> findByBuyerRef(String buyerRef);
}
