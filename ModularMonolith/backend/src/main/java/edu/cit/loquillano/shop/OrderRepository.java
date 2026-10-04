package edu.cit.loquillano.shop;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findAllByStatusOrderByCreatedAtAsc(String status);

    /** Just the ids: avoids loading every order and its items (one extra query per order). */
    @Query("select o.orderId from Order o where o.status = :status order by o.createdAt asc")
    List<Long> findIdsByStatus(@Param("status") String status);

    /** Units of one product promised to orders in the given status, in a single query. */
    @Query("select coalesce(sum(i.quantity), 0) from OrderItem i where i.order.status = :status and i.productId = :productId")
    long sumQuantityByStatusAndProduct(@Param("status") String status, @Param("productId") String productId);

    /** Row lock so cancelling and backorder-filling the same order cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
