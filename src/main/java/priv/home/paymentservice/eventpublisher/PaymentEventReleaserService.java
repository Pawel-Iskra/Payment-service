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

import java.time.OffsetDateTime;
import java.util.List;
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
        int currentBatchSize;
        ScrollPosition position = ScrollPosition.keyset();
        do {
            Window<OutboxEventEntity> eventBatchToPublishWindow = outboxStorageJpa
                    .findByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAscEventIdAsc(position, Limit.of(BATCH_SIZE));
            currentBatchSize = eventBatchToPublishWindow.size();
            if (!eventBatchToPublishWindow.isEmpty() && eventBatchToPublishWindow.hasNext()) {
                position = eventBatchToPublishWindow.positionAt(currentBatchSize - 1);
            }

            List<OutboxEventEntity> outboxEventsEntityToPublish = eventBatchToPublishWindow.stream().toList();
            List<OutboxEventToPublish> eventsToPublish = outboxEventsEntityToPublish
                    .stream()
                    .map(this::getOutboxEventToPublish)
                    .toList();
            log.info("Amount of events to publish = {}", eventsToPublish.size());

            List<UUID> eventIdsToPublish = eventsToPublish.stream().map(OutboxEventToPublish::eventId).toList();
            if (eventsToPublish.isEmpty()) {
                log.info("No unpublished events found");
                return;
            }
            List<UUID> acknowledgedEventIds = paymentEventPublisher.publishPaymentEvents(eventsToPublish);
            log.info("Amount of published events acknowledged by publisher = {}", acknowledgedEventIds.size());
            // TODO: update all attempt_count
            int eventsUpdatedAsPublished = outboxStorageJpa.markEventsAsPublished(acknowledgedEventIds);
            log.info("Events updated as published = {}", eventsUpdatedAsPublished);

            List<OutboxEventEntity> eventsEntityNotPublished = outboxEventsEntityToPublish.stream()
                    .filter(event -> !acknowledgedEventIds.contains(event.getEventId()))
                    .toList();
            updateNotPublishedOutboxEventEntity(eventsEntityNotPublished);
            List<OutboxEventEntity> updatedNotPublished = outboxStorageJpa.saveAll(eventsEntityNotPublished);
            log.info("Events updated as not published = {}",
                    updatedNotPublished.stream().map(OutboxEventEntity::getEventId).toList());

        } while (currentBatchSize == BATCH_SIZE);
    }

    private void updateNotPublishedOutboxEventEntity(List<OutboxEventEntity> eventsEntityNotPublished) {
        for (OutboxEventEntity currentEvent : eventsEntityNotPublished) {
            currentEvent.setAttempt_count(currentEvent.getAttempt_count() + 1);
            int attempts = currentEvent.getAttempt_count();
            if (attempts == MAX_ATTEMPT) {
                currentEvent.setFailedAt(OffsetDateTime.now());
                currentEvent.setLastError("ERROR TO BE PASSED"); // TODO: need to have error here
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
