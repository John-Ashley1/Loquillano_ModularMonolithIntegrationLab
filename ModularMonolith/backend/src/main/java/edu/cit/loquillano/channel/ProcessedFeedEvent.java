package edu.cit.loquillano.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/** One row per feed eventId we have handled: Tiangge may deliver the same event twice (with a new seq). */
@Entity
@Table(name = "channel_events")
class ProcessedFeedEvent implements Persistable<String> {

    @Id
    @Column(name = "event_id", length = 60)
    private String eventId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "tiangge_order_id")
    private String tianggeOrderId;

    @Column(name = "seq")
    private Long seq;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Transient
    private boolean fresh;

    protected ProcessedFeedEvent() {
        // required by JPA
    }

    ProcessedFeedEvent(String eventId, String type, String tianggeOrderId, long seq) {
        this.eventId = eventId;
        this.type = type;
        this.tianggeOrderId = tianggeOrderId;
        this.seq = seq;
        this.processedAt = LocalDateTime.now();
        this.fresh = true;
    }

    @PostPersist
    void markPersisted() {
        this.fresh = false;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }
}
