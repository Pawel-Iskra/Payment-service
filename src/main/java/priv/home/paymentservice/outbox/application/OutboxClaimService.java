package priv.home.paymentservice.outbox.application;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class OutboxClaimService {

    private final OutboxStorageJpa outboxStorageJpa;
    private final OutboxEventPublisherProperties publisherProperties;


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
    public void completeClaim(UUID claimToken, List<PublishResult> publishResults) {
        List<OutboxEventEntity> retrievedClaimedEvents = outboxStorageJpa.findAllByClaimToken(claimToken);
        Map<UUID, PublishResult> publishResultMapByEventId = publishResults
                .stream()
                .collect(Collectors.toMap(
                        PublishResult::getEventId,
                        Function.identity()
                ));
        for (OutboxEventEntity eventEntity : retrievedClaimedEvents) {
            PublishResult publishResult = publishResultMapByEventId.get(eventEntity.getEventId());
            if (publishResult == null) {
                // ??
            }

            if (publishResult.isPublished()) {
                eventEntity.markAsPublished();
            } else {
                int maxAttempts = publisherProperties.retry().maxAttempt();
                int attemptsDone = eventEntity.getAttemptCount();
                OffsetDateTime nextAttemptAt = OffsetDateTime.now().plus(calculateBackoff(attemptsDone));
                eventEntity.markAsNotPublished(publishResult.getErrorInfo(), maxAttempts, nextAttemptAt);
            }
        }

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

    private Duration calculateBackoff(int attemptsDone) {
        OutboxEventPublisherProperties.Retry retryProperties = publisherProperties.retry();
        int multiplier = Math.powExact(retryProperties.multiplier(), attemptsDone);
        Duration calculatedBackoff = retryProperties.initialBackoff().multipliedBy(multiplier);
        return calculatedBackoff.compareTo(retryProperties.maxBackoff()) > 0 ?
                retryProperties.maxBackoff() : calculatedBackoff;
    }
}