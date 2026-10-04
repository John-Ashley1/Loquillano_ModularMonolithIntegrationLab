package edu.cit.loquillano.channel;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Keeps what we tell Tiangge consistent. Tiangge keeps its own books from the decisions it
 * RECEIVES, so a stock update must never travel to Tiangge at the same moment as a decision
 * it should (or should not) reflect:
 *  - any number of decisions / resolutions / cancellation confirmations may be in flight together
 *    (shared "decisions" side);
 *  - a stock update is exclusive: it waits until every message in flight has been delivered and
 *    recorded in our database, and no new one starts until the update has gone out.
 * Fair ordering, so a waiting stock update is not starved by a steady stream of decisions.
 */
@Component
class TianggeWriteGate {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    /** Held while sending a decision, resolution or cancellation confirmation. */
    Lock decisions() {
        return lock.readLock();
    }

    /** Held while computing and sending a stock update. */
    Lock stock() {
        return lock.writeLock();
    }
}
