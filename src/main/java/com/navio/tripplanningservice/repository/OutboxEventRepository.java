package com.navio.tripplanningservice.repository;

import com.navio.tripplanningservice.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims due, unpublished events for this relay instance.
     *
     * <p>{@code PESSIMISTIC_WRITE} with {@code SKIP LOCKED} (lock timeout -2) lets
     * several replicas drain the table concurrently without sending a row twice
     * from two instances at once or blocking each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.publishedAt IS NULL AND e.nextAttemptAt <= :now AND e.attemptCount < :maxAttempts
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> claimDue(@Param("now") Instant now, @Param("maxAttempts") int maxAttempts, Pageable page);

    long countByPublishedAtIsNull();

    /** Rows that gave up retrying and need an operator. */
    long countByPublishedAtIsNullAndAttemptCountGreaterThanEqual(int maxAttempts);

    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.publishedAt IS NULL")
    Instant oldestUnpublished();

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
