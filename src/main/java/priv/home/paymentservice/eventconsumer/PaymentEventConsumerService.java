package priv.home.paymentservice.eventconsumer;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.eventconsumer.data.ConsumedEventEntity;
import priv.home.paymentservice.eventconsumer.data.ConsumedEventStorageJpa;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
@Service
public class PaymentEventConsumerService {


    private final ConsumedEventStorageJpa consumedEventStorageJpa;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<ConsumedEventEntity> processConsumedEvent(String payload) {
        PaymentCreatedEvent eventReceived = objectMapper.readValue(payload, PaymentCreatedEvent.class);
        UUID eventReceivedId = eventReceived.getEventId();

        Optional<ConsumedEventEntity> insertedEventOptional = consumedEventStorageJpa.insertConsumedEventIfAbsent(eventReceivedId);
        if (insertedEventOptional.isEmpty()) {
            log.info("Duplicate event ignored, evenId={}", eventReceivedId);
            return Optional.empty();
        }
        log.info("Event accepted for business processing, eventId={}", insertedEventOptional.get().getEventId());
        // Business logic to be executed here.
        return insertedEventOptional;
    }
}
