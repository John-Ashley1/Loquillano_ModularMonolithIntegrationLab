package edu.cit.loquillano.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface FeedCursorRepository extends JpaRepository<FeedCursor, String> {
}
