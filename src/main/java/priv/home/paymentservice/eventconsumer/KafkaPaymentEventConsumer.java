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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@AllArgsConstructor
@Service
public class KafkaPaymentEventConsumer {

    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAYMENT_ID_PATTERN = Pattern.compile("\"paymentId\"\\s*:\\s*\"([^\"]+)\"");

    private final PaymentEventConsumerService paymentEventConsumerService;


    @KafkaListener(
            topics = "${payment-service.kafka-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consumePaymentEventFromKafka(ConsumerRecord<String, String> consumedRecord) {
        Header header = consumedRecord.headers().lastHeader(CORRELATION_ID_HEADER);
        String correlationId = header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);

            String payload = consumedRecord.value();
            log.info("Consumed payment eventId={}, paymentId={}",
                    extractValue(payload, EVENT_ID_PATTERN),
                    extractValue(payload, PAYMENT_ID_PATTERN));
            Optional<ConsumedEventEntity> consumedEventInsertedOptional =
                    paymentEventConsumerService.processConsumedEvent(payload);
            if (consumedEventInsertedOptional.isPresent()) {
                log.info("Payment event successfully processed, consumedEventEntity={}", consumedEventInsertedOptional.get());
            } else {
                log.info("Payment event was not processed (duplicate or already registered)");
            }
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }


    private String extractValue(String payload, Pattern pattern) {
        Matcher matcher = pattern.matcher(payload);
        return matcher.find() ? matcher.group(1) : null;
    }
}
