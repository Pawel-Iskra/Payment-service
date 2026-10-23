package priv.home.paymentservice.data;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

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

    Window<OutboxEventEntity> findByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAscEventIdAsc(
            ScrollPosition position, Limit limit
    );
}
