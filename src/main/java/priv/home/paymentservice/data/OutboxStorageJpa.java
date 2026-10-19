package priv.home.paymentservice.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.model.OutboxEventEntity;

import java.util.List;
import java.util.UUID;

public interface OutboxStorageJpa extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findAllByPublishedAtIsNull();

    @Transactional
    @Modifying
    @Query("""
            UPDATE OutboxEventEntity e
            SET e.publishedAt = CURRENT_TIMESTAMP
            WHERE e.eventId in :listOfIdsToUpdate
            """)
    int markEventsAsPublished(List<UUID> listOfIdsToUpdate);
}
