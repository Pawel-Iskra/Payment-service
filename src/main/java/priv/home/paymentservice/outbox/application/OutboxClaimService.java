package priv.home.paymentservice.outbox.application;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OutboxClaimService {

    private final OutboxStorageJpa outboxStorageJpa;


    @Transactional
    public ReservedOutboxBatch reserveEvents(String workerId, int batchSize, Duration leaseDuration) {
        OffsetDateTime now = OffsetDateTime.now();
        List<OutboxEventEntity> retrievedEvents = outboxStorageJpa
                .reserveEventsForPublish(now, PageRequest.of(0, batchSize));
        if (retrievedEvents.isEmpty()) {
            return ReservedOutboxBatch.builder()
                    .eventsToPublish(Collections.emptyList())
                    .build();
        }
        UUID claimToken = UUID.randomUUID();
        OffsetDateTime lockedUntil = now.plus(leaseDuration);
        retrievedEvents.forEach(
                event -> event.reserve(workerId, lockedUntil, claimToken)
        );
        return buildReservedOutboxBatch(retrievedEvents, claimToken);
    }

    @Transactional
    public void completeClaim(UUID uuid, List<PublishResult> publishResults) {

    }


    private ReservedOutboxBatch buildReservedOutboxBatch(List<OutboxEventEntity> outboxEventEntities, UUID claimToken) {
        List<OutboxEventToPublish> outboxEventsToPublish = outboxEventEntities.stream()
                .map(this::buildEventToPublish)
                .toList();
        return ReservedOutboxBatch.builder()
                .claimToken(claimToken)
                .eventsToPublish(outboxEventsToPublish)
                .build();
    }

    private OutboxEventToPublish buildEventToPublish(OutboxEventEntity outboxEventEntity) {
        return OutboxEventToPublish.builder()
                .eventId(outboxEventEntity.getEventId())
                .paymentId(outboxEventEntity.getPaymentId())
                .correlationId(outboxEventEntity.getCorrelationId())
                .eventType(outboxEventEntity.getEventType())
                .payload(outboxEventEntity.getPayload())
                .build();
    }
}
