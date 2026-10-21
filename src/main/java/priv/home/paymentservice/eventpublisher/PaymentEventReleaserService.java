package priv.home.paymentservice.eventpublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxEventEntity;
import priv.home.paymentservice.data.OutboxStorageJpa;
import priv.home.paymentservice.eventpublisher.model.OutboxEventToPublish;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventReleaserService {

    private final OutboxStorageJpa outboxStorageJpa;
    private final PaymentEventPublisher paymentEventPublisher;


    public void publishPaymentCreatedEvents() {
        List<OutboxEventToPublish> eventsToPublish = outboxStorageJpa.findAllByPublishedAtIsNull()
                .stream()
                .map(this::getOutboxEventToPublish)
                .toList();
        log.info("Amount of events to publish = {}", eventsToPublish.size());
        if (eventsToPublish.isEmpty()) {
            log.info("No unpublished events found");
            return;
        }
        List<UUID> acknowledgedEventIds = paymentEventPublisher.publishPaymentEvents(eventsToPublish);
        log.info("Amount of published events acknowledged by publisher = {}", acknowledgedEventIds.size());
        int updatedRecords = outboxStorageJpa.markEventsAsPublished(acknowledgedEventIds);
        log.info("Updated events in outbox_event = {}", updatedRecords);
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
