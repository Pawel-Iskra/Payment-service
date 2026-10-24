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

    // batch will be configurable
    private static final int BATCH_SIZE = 3;
    // max attempt will be configurable
    private static final int MAX_ATTEMPT = 2;


    public void publishPaymentCreatedEvents() {
        ScrollPosition position = ScrollPosition.keyset();
        boolean hasNext;

        do {
            Window<OutboxEventEntity> eventBatchToPublishWindow = outboxStorageJpa
                    .findByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAscEventIdAsc(position, Limit.of(BATCH_SIZE));
            hasNext = eventBatchToPublishWindow.hasNext();
            if (!eventBatchToPublishWindow.isEmpty() && eventBatchToPublishWindow.hasNext()) {
                position = eventBatchToPublishWindow.positionAt(eventBatchToPublishWindow.size() - 1);
            }
            if (eventBatchToPublishWindow.isEmpty()) {
                log.info("No unpublished events found");
                return;
            }

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

    private void updateOutboxEventEntitiesAfterPublish(Map<UUID, OutboxEventEntity> outboxEventEntityMap, List<PublishResult> publishResultList) {
        for (PublishResult currentPublishResult : publishResultList) {
            UUID eventId = currentPublishResult.getEventId();
            OutboxEventEntity currentOutboxEventEntity = outboxEventEntityMap.get(eventId);
            if (currentPublishResult.isPublished()) {
                currentOutboxEventEntity.markAsPublished();
            } else {
                currentOutboxEventEntity.markAsNotPublished(currentPublishResult.getErrorInfo(), MAX_ATTEMPT);
            }
        }
    }

    private OutboxEventToPublish getOutboxEventToPublish(OutboxEventEntity outboxEventEntity) {
        return OutboxEventToPublish.builder()
                .eventId(outboxEventEntity.getEventId())
                .paymentId(outboxEventEntity.getPaymentId())
                .eventType(outboxEventEntity.getEventType())
                .payload(outboxEventEntity.getPayload())
                .build();
    }
}
