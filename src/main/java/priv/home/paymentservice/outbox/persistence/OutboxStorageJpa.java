package priv.home.paymentservice.outbox.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxStorageJpa extends JpaRepository<OutboxEventEntity, UUID> {

    @Transactional
    @Modifying
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.publishedAt = CURRENT_TIMESTAMP
            WHERE e.eventId in :listOfIdsToUpdate
            """)
    int markEventsAsPublished(List<UUID> listOfIdsToUpdate);

    List<OutboxEventEntity> findAllByPublishedAtIsNull();

    Window<OutboxEventEntity>
    findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
            OffsetDateTime now, ScrollPosition position, Limit limit);


    @SelectForUpdateSkipLocked
    @Query("""
            SELECT e
            FROM OutboxEventEntity e
            WHERE e.publishedAt IS NULL
              AND e.failedAt IS NULL
              AND e.nextAttemptAt <= :now
              AND (e.lockedUntil IS NULL OR e.lockedUntil <= :now)
            ORDER BY e.createdAt ASC, e.eventId ASC
            """)
    List<OutboxEventEntity> reserveEventsForPublish(
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEventEntity> findAllByClaimToken(UUID claimToken);
}
