package edu.cit.loquillano.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The durable "I have read the feed up to here" bookmark. */
@Entity
@Table(name = "channel_cursor")
class FeedCursor {

    @Id
    @Column(name = "id", length = 40)
    private String id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    protected FeedCursor() {
        // required by JPA
    }

    FeedCursor(String id, long lastSeq) {
        this.id = id;
        this.lastSeq = lastSeq;
    }

    String getId() {
        return id;
    }

    long getLastSeq() {
        return lastSeq;
    }

    void setLastSeq(long lastSeq) {
        this.lastSeq = lastSeq;
    }
}
