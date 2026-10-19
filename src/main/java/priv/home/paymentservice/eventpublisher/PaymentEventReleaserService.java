package priv.home.paymentservice.eventpublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxEventToPublish;
import priv.home.paymentservice.data.OutboxStorageJpa;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.OutboxEventEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventReleaserService {

    private final OutboxStorageJpa outboxStorageJpa;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;


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
        return new OutboxEventToPublish(
                outboxEventEntity.getEventId(),
                objectMapper.readValue(outboxEventEntity.getPayload(), PaymentCreatedEvent.class)
        ); // TODO: think of error handling if one failed
    }
}
