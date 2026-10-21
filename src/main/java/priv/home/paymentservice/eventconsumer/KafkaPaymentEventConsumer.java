package priv.home.paymentservice.eventconsumer;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.eventconsumer.data.ConsumedEventEntity;

import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class KafkaPaymentEventConsumer {

    private final PaymentEventConsumerService paymentEventConsumerService;

    @KafkaListener(
            topics = "${payment-service.kafka-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEventFromKafka(String payload) {
        log.info("Consumed payment event={}", payload);
        Optional<ConsumedEventEntity> consumedEventInsertedOptional = paymentEventConsumerService.processConsumedEvent(payload);
        if (consumedEventInsertedOptional.isPresent()) {
            log.info("Payment event successfully processed, consumedEventEntity={}", consumedEventInsertedOptional.get());
        } else {
            log.info("Payment event was not processed (duplicate or already registered)");
        }
    }
}
