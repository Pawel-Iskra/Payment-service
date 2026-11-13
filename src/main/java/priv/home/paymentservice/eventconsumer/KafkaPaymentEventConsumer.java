package priv.home.paymentservice.eventconsumer;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.eventconsumer.persistence.ConsumedEventEntity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class KafkaPaymentEventConsumer {

    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final PaymentEventConsumerService paymentEventConsumerService;


    @KafkaListener(
            topics = "${payment-service.kafka-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEventFromKafka(ConsumerRecord<String, String> consumedRecord) {
        Header header = consumedRecord.headers().lastHeader(CORRELATION_ID_HEADER);
        String correlationId = header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            log.info("Consumed payment event={}", consumedRecord.value());
            Optional<ConsumedEventEntity> consumedEventInsertedOptional =
                    paymentEventConsumerService.processConsumedEvent(consumedRecord.value());
            if (consumedEventInsertedOptional.isPresent()) {
                log.info("Payment event successfully processed, consumedEventEntity={}", consumedEventInsertedOptional.get());
            } else {
                log.info("Payment event was not processed (duplicate or already registered)");
            }
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
