package edu.cit.loquillano.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

interface ChannelOrderRepository extends JpaRepository<ChannelOrder, String> {

    /** Anything we still owe Tiangge: a decision, a backorder resolution or a cancellation confirmation. */
    String OUTSTANDING = "c.decisionSent = false"
            + " or (c.resolution is not null and c.resolutionSent = false)"
            + " or (c.cancelRequested = true and c.cancelConfirmed = false)";

    @Query("select case when count(c) > 0 then true else false end from ChannelOrder c where " + OUTSTANDING)
    boolean existsOutstanding();

    @Query("select c from ChannelOrder c where " + OUTSTANDING + " order by c.createdAt asc")
    List<ChannelOrder> findOutstanding();

    Optional<ChannelOrder> findByShopOrderId(Long shopOrderId);

    /** Accepted orders whose stock was already taken locally but whose acceptance Tiangge has not been told. */
    @Query("select c from ChannelOrder c where (c.decision = 'ACCEPTED' and c.decisionSent = false)"
            + " or (c.resolution = 'ACCEPTED' and c.resolutionSent = false)")
    List<ChannelOrder> findUnannouncedAccepted();

    /** Cancelled orders whose stock is already back locally but whose cancellation Tiangge has not been told. */
    @Query("select c from ChannelOrder c where c.cancelRequested = true and c.cancelConfirmed = false"
            + " and (c.decision = 'ACCEPTED' or c.resolution = 'ACCEPTED')")
    List<ChannelOrder> findUnconfirmedCancelsHoldingStock();

    // Targeted updates, so a slow sender can never overwrite fields another thread just changed.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update ChannelOrder c set c.decisionSent = true where c.tianggeOrderId = :id")
    int markDecisionSent(@Param("id") String id);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update ChannelOrder c set c.resolutionSent = true where c.tianggeOrderId = :id")
    int markResolutionSent(@Param("id") String id);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update ChannelOrder c set c.resolution = :resolution, c.resolutionSent = false where c.tianggeOrderId = :id")
    int markResolution(@Param("id") String id, @Param("resolution") String resolution);

    /** Same update, but joins the caller's transaction so it commits atomically with it. */
    @Transactional
    @Modifying
    @Query("update ChannelOrder c set c.resolution = :resolution, c.resolutionSent = false where c.tianggeOrderId = :id")
    int markResolutionInTx(@Param("id") String id, @Param("resolution") String resolution);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("update ChannelOrder c set c.cancelConfirmed = true where c.tianggeOrderId = :id")
    int markCancelConfirmed(@Param("id") String id);
}
