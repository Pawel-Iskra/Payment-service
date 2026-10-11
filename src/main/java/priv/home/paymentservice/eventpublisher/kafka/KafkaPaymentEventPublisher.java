package priv.home.paymentservice.eventpublisher.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.eventpublisher.PaymentEventPublisher;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.Payment;

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
    public void publish(String idempotencyKey, Payment paymentToPublish) {
        PaymentCreatedEvent paymentCreatedEvent = getPaymentCreatedEventFromPayment(idempotencyKey, paymentToPublish);
        try {
            kafkaTemplate.send(kafkaTopic, paymentCreatedEvent.getPaymentId().toString(), paymentCreatedEvent)
                    .whenComplete((result, exception) -> { // handle asynchronous completion of the send operation
                        if (exception != null) {
                            log.error("Error while publishing PaymentCreatedEvent, paymentId={}",
                                    paymentCreatedEvent.getPaymentId(),
                                    exception);
                            return;
                        }
                        log.info("PaymentCreatedEvent successfully acknowledged by Kafka, paymentId={}",
                                paymentCreatedEvent.getPaymentId());
                    });
        } catch (Exception exception) { // handle synchronous exceptions during send() invocation
            log.error("Failed to initiate publishing of PaymentCreatedEvent, paymentId={}",
                    paymentCreatedEvent.getPaymentId(),
                    exception);
        }
    }


    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(String idempotencyKey, Payment paymentToPublish) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentToPublish.getPaymentId())
                .amount(paymentToPublish.getAmount())
                .currency(paymentToPublish.getCurrency())
                .paymentStatus(paymentToPublish.getPaymentStatus())
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
