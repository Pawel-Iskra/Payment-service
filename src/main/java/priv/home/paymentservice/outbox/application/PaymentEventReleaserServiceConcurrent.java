package priv.home.paymentservice.outbox.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventReleaserServiceConcurrent {

    private final PaymentEventPublisher paymentEventPublisher;
    private final OutboxEventPublisherProperties publisherProperties;
    private final OutboxClaimService outboxClaimService;
    private final ExecutorService executorService;


    public void publishPaymentCreatedEvents() {
        int workers = publisherProperties.publishingWorkerCount();
        for (int i = 0; i < workers; i++) {
            String workerId = "outbox-worker-" + i;
            executorService.submit(() -> processEvents(workerId));
        }
    }


    private void processEvents(String workerId) {
        log.info("Outbox worker started : {}", workerId);
        int batchSize = publisherProperties.batchSize();
        Duration leaseDuration = publisherProperties.leaseDuration();

        ReservedOutboxBatch batch;
        do {
            batch = outboxClaimService.reserveEvents(workerId, batchSize, leaseDuration);
            if (!batch.eventsToPublish().isEmpty()) {
                List<PublishResult> publishResults = paymentEventPublisher.publishPaymentEvents(batch.eventsToPublish());
                outboxClaimService.completeClaim(batch.claimToken(), publishResults);
            }

        } while (!batch.eventsToPublish().isEmpty());

        log.info("Outbox worker finished: {}", workerId);
    }

    private Map<UUID, OutboxEventEntity> getOutboxEventEntityMap(List<OutboxEventEntity> outboxEventsEntityToPublish) {
        Map<UUID, OutboxEventEntity> resultMap = new HashMap<>();
        outboxEventsEntityToPublish.forEach(event -> resultMap.put(event.getEventId(), event));
        return resultMap;
    }

    private OutboxEventToPublish getOutboxEventToPublish(OutboxEventEntity outboxEventEntity) {
        return OutboxEventToPublish.builder()
                .eventId(outboxEventEntity.getEventId())
                .paymentId(outboxEventEntity.getPaymentId())
                .correlationId(outboxEventEntity.getCorrelationId())
                .eventType(outboxEventEntity.getEventType())
                .payload(outboxEventEntity.getPayload())
                .build();
    }

    private void updateOutboxEventEntitiesAfterPublish(Map<UUID, OutboxEventEntity> outboxEventEntityMap, List<PublishResult> publishResultList) {
        for (PublishResult currentPublishResult : publishResultList) {
            UUID eventId = currentPublishResult.getEventId();
            OutboxEventEntity currentOutboxEventEntity = outboxEventEntityMap.get(eventId);
            if (currentPublishResult.isPublished()) {
                currentOutboxEventEntity.markAsPublished();
            } else {
                int attemptsDone = currentOutboxEventEntity.getAttemptCount();
                OffsetDateTime nextAttemptAt = OffsetDateTime.now().plus(calculateBackoff(attemptsDone));
                currentOutboxEventEntity.markAsNotPublished(
                        currentPublishResult.getErrorInfo(), publisherProperties.retry().maxAttempt(), nextAttemptAt);
            }
        }
    }

    private Duration calculateBackoff(int attemptsDone) {
        OutboxEventPublisherProperties.Retry retryProperties = publisherProperties.retry();
        int multiplier = Math.powExact(retryProperties.multiplier(), attemptsDone);
        Duration calculatedBackoff = retryProperties.initialBackoff().multipliedBy(multiplier);
        return calculatedBackoff.compareTo(retryProperties.maxBackoff()) > 0 ?
                retryProperties.maxBackoff() : calculatedBackoff;
    }
}
