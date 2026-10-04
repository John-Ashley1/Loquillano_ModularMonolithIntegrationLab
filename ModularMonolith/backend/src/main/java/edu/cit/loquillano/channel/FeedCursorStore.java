package edu.cit.loquillano.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Remembers where we stopped reading the feed, in the database, so after a
 * restart we continue from there instead of starting over. The cursor only
 * ever moves forward.
 */
@Component
class FeedCursorStore {

    private static final Logger log = LoggerFactory.getLogger(FeedCursorStore.class);
    private static final String ID = "tiangge-feed";

    private final FeedCursorRepository repository;
    private volatile Long cached;

    FeedCursorStore(FeedCursorRepository repository) {
        this.repository = repository;
    }

    synchronized long get() {
        if (cached == null) {
            cached = repository.findById(ID).map(FeedCursor::getLastSeq).orElse(0L);
            log.info("Feed cursor loaded from database: {}", cached);
        }
        return cached;
    }

    synchronized void save(long seq) {
        long current = get();
        if (seq <= current) {
            return;
        }
        FeedCursor row = repository.findById(ID).orElseGet(() -> new FeedCursor(ID, 0));
        row.setLastSeq(seq);
        repository.save(row);
        cached = seq;
    }
}
