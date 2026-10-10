package priv.home.paymentservice.eventcomsumer;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;

@Service
@Slf4j
@AllArgsConstructor
public class PaymentEventConsumer {


    @KafkaListener(
            topics = "${payment-service.kafka-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEventFromKafka(PaymentCreatedEvent paymentCreatedEvent) {
        log.info("Received payment event : {}", paymentCreatedEvent);
    }
}
