package priv.home.paymentservice.eventpublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxEventEntity;
import priv.home.paymentservice.data.OutboxStorageJpa;
import priv.home.paymentservice.eventpublisher.model.OutboxEventToPublish;
import priv.home.paymentservice.eventpublisher.model.PublishResult;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventReleaserService {

    private final OutboxStorageJpa outboxStorageJpa;
    private final PaymentEventPublisher paymentEventPublisher;
    private final OutboxEventPublisherProperties publisherProperties;


    public void publishPaymentCreatedEvents() {
        ScrollPosition position = ScrollPosition.keyset();
        boolean hasNext;

        do {
            Window<OutboxEventEntity> eventBatchToPublishWindow = outboxStorageJpa
                    .findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
                            OffsetDateTime.now(), position, Limit.of(publisherProperties.batchSize()));
            if (eventBatchToPublishWindow.isEmpty()) {
                log.info("No unpublished events found");
                return;
            }
            hasNext = eventBatchToPublishWindow.hasNext();
            position = eventBatchToPublishWindow.positionAt(eventBatchToPublishWindow.size() - 1);

            List<OutboxEventEntity> outboxEventsEntityToPublish = eventBatchToPublishWindow.getContent();
            Map<UUID, OutboxEventEntity> outboxEventEntityMap = getOutboxEventEntityMap(outboxEventsEntityToPublish);
            log.info("Amount of events to publish = {}", outboxEventsEntityToPublish.size());
            List<OutboxEventToPublish> eventsToPublish = outboxEventsEntityToPublish
                    .stream()
                    .map(this::getOutboxEventToPublish)
                    .toList();
            List<PublishResult> publishResultList = paymentEventPublisher.publishPaymentEvents(eventsToPublish);
            log.info("Amount of published events acknowledged by publisher = {}",
                    publishResultList.stream().filter(PublishResult::isPublished).toList().size());
            updateOutboxEventEntitiesAfterPublish(outboxEventEntityMap, publishResultList);
            outboxStorageJpa.saveAll(outboxEventEntityMap.values());
        } while (hasNext);
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
