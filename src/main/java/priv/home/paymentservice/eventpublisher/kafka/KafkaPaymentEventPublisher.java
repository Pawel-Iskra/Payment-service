package priv.home.paymentservice.eventpublisher.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxEventToPublish;
import priv.home.paymentservice.eventpublisher.PaymentEventPublisher;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {


    private final String kafkaTopic;
    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;


    public KafkaPaymentEventPublisher(
            @Value("${payment-service.kafka-topic}") String kafkaTopic, // TODO: @ConfigurationProperties
            KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate) {
        this.kafkaTopic = kafkaTopic;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public List<UUID> publishPaymentEvents(Collection<OutboxEventToPublish> eventsToPublish) {
        return eventsToPublish.stream()
                .map(event -> handle(event, send(event)))
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }
    // TODO: analyze more

    private CompletableFuture<SendResult<String, PaymentCreatedEvent>> send(OutboxEventToPublish outboxEvent) {
        PaymentCreatedEvent event = outboxEvent.paymentCreatedEvent();
        return kafkaTemplate.send(
                kafkaTopic,
                event.getPaymentId().toString(),
                event);
    }

    private CompletableFuture<UUID> handle(OutboxEventToPublish outboxEvent,
            CompletableFuture<SendResult<String, PaymentCreatedEvent>> sendFuture) {

        return sendFuture.handle((result, exception) -> {
            if (exception != null) {
                log.error("Failed to publish eventId={}", outboxEvent.eventId(), exception);
                return null;
            }
            log.info("Event acknowledged by Kafka, eventId={}", outboxEvent.eventId());
            return outboxEvent.eventId();
        });
    }
}
