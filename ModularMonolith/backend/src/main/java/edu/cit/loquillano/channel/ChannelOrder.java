package edu.cit.loquillano.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * Our record of one Tiangge order: which of OUR orders it became, what we
 * decided, and which messages to Tiangge are still owed. This row is what
 * makes processing exactly-once (primary key = Tiangge's order id) and what
 * lets a restarted app finish any decision / resolution / cancellation
 * confirmation that was not delivered before it stopped.
 */
@Entity
@Table(name = "channel_orders")
class ChannelOrder implements Persistable<String> {

    @Id
    @Column(name = "tiangge_order_id", length = 40)
    private String tianggeOrderId;

    @Column(name = "shop_order_id", nullable = false)
    private Long shopOrderId;

    @Column(name = "decision", nullable = false)
    private String decision;

    @Column(name = "decision_sent", nullable = false)
    private boolean decisionSent;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "resolution_sent", nullable = false)
    private boolean resolutionSent;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "cancel_confirmed", nullable = false)
    private boolean cancelConfirmed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Transient
    private boolean fresh;

    protected ChannelOrder() {
        // required by JPA; entities loaded from the database are not new
    }

    ChannelOrder(String tianggeOrderId, Long shopOrderId, String decision) {
        this.tianggeOrderId = tianggeOrderId;
        this.shopOrderId = shopOrderId;
        this.decision = decision;
        this.createdAt = LocalDateTime.now();
        this.fresh = true;
    }

    @PostPersist
    void markPersisted() {
        this.fresh = false;
    }

    @Override
    public String getId() {
        return tianggeOrderId;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    String getTianggeOrderId() {
        return tianggeOrderId;
    }

    Long getShopOrderId() {
        return shopOrderId;
    }

    String getDecision() {
        return decision;
    }

    boolean isDecisionSent() {
        return decisionSent;
    }

    String getResolution() {
        return resolution;
    }

    boolean isResolutionSent() {
        return resolutionSent;
    }

    boolean isCancelRequested() {
        return cancelRequested;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    boolean isCancelConfirmed() {
        return cancelConfirmed;
    }

    void requestResolution(String resolution) {
        this.resolution = resolution;
        this.resolutionSent = false;
    }

    void requestCancelConfirmation() {
        this.cancelRequested = true;
        this.cancelConfirmed = false;
    }
}
