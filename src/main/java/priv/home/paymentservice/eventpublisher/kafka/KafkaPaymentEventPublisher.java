package priv.home.paymentservice.eventpublisher.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.eventpublisher.PaymentEventPublisher;
import priv.home.paymentservice.eventpublisher.model.OutboxEventToPublish;
import priv.home.paymentservice.eventpublisher.model.PublishResult;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {


    private final String kafkaTopic;
    private final KafkaTemplate<String, String> kafkaTemplate;


    public KafkaPaymentEventPublisher(
            @Value("${payment-service.kafka-topic}") String kafkaTopic, // TODO: @ConfigurationProperties
            KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTopic = kafkaTopic;
        this.kafkaTemplate = kafkaTemplate;
    }


    @Override
    public List<PublishResult> publishPaymentEvents(Collection<OutboxEventToPublish> eventsToPublish) {
        List<CompletableFuture<PublishResult>> futuresAfterSendHandle = eventsToPublish.stream()
                .map(event -> handle(event, send(event)))
                .toList();

        return futuresAfterSendHandle.stream()
                .map(CompletableFuture::join)
                .toList();
    } // here - handle is asynchronous but join is synchronous


    private CompletableFuture<SendResult<String, String>> send(OutboxEventToPublish outboxEvent) {
        try {
            return kafkaTemplate.send(
                    kafkaTopic,
                    outboxEvent.paymentId().toString(),
                    outboxEvent.payload());
        } catch (Exception exception) { // synchronous exception thrown by send (before future returned)
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletableFuture<PublishResult> handle(OutboxEventToPublish outboxEvent,
                                                    CompletableFuture<SendResult<String, String>> sendResultFuture) {
        UUID eventId = outboxEvent.eventId();
        return sendResultFuture.handle((sendResult, exception) -> {
            if (exception != null) {
                log.error("Failed to publish the event, eventId={}", eventId, exception);
                return PublishResult.buildNotPublished(eventId, exception);
            }
            // Defensive check for unlikely case when the future completes successfully but sendResult is null
            // since delivery status is uncertain, the publisher will retry to preserve at-least-once delivery
            // the broker may have received the event, so a retry could result in a duplicate which idempotent consumer can handle
            if (sendResult == null) {
                return PublishResult.buildNotPublished(
                        eventId, new IllegalStateException("Kafka send completed without a result"));
            }
            log.info("Event acknowledged by Kafka, eventId={}", eventId);
            return PublishResult.buildPublished(eventId);
        });
    }
}
