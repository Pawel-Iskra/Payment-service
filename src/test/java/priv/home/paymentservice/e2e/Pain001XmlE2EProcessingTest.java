package priv.home.paymentservice.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.*;
import priv.home.paymentservice.eventconsumer.persistence.ConsumedEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.payment.iso20022.Pain001TestData;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
public class Pain001XmlE2EProcessingTest extends AbstractE2EProcessingTest {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final String PAIN001_XML_POST_PATH = "/payments/pain001";
    private static final String CONSUMED_PAYMENT_EVENT = "Consumed payment event=%s";
    private static final String CONSUMED_EVENT_SUCCESSFULLY_PROCESSED = "Payment event successfully processed, consumedEventEntity=%s";
    private static final String CONSUMED_EVENT_NOT_PROCESSED = "Payment event was not processed (duplicate or already registered)";
    private static final String CONSUMED_EVENT_ALREADY_EXIST_IN_DB = "Event already exist in db, eventId=%s";


    @Test
    public void shouldPublishEventToKafkaAndNextThisEventShouldBeConsumedAndPersistedByConsumer(CapturedOutput capturedOutput) {
        // given
        OffsetDateTime timeBeforePaymentCreate = OffsetDateTime.now().minusSeconds(1);
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, IDEMPOTENCY_KEY_VALUE);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when
        ResponseEntity<String> response = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
                httpRequest,
                String.class);

        // then
        List<OutboxEventEntity> outboxEventEntities = outboxStorageJpa.findAll();
        assertThat(outboxEventEntities).hasSize(1);
        UUID eventId = outboxEventEntities.getFirst().getEventId();
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(consumedEventStorageJpa.findById(eventId)).isNotEmpty();
                });
        List<ConsumedEventEntity> consumedEventEntities = consumedEventStorageJpa.findAll();
        OffsetDateTime timeAfterEventConsumed = OffsetDateTime.now().plusSeconds(1);
        assertThat(consumedEventEntities)
                .singleElement()
                .satisfies(consumedEventEntity -> {
                    assertThat(consumedEventEntity.getEventId()).isEqualTo(eventId);
                    assertThat(consumedEventEntity.getConsumedAt())
                            .isAfter(timeBeforePaymentCreate)
                            .isBefore(timeAfterEventConsumed);
                    assertThat(capturedOutput.getOut())
                            .contains(CONSUMED_EVENT_SUCCESSFULLY_PROCESSED.formatted(consumedEventEntity));
                });
        Optional<OutboxEventEntity> outboxEventEntityOptional = outboxStorageJpa.findById(eventId);
        assertThat(outboxEventEntityOptional)
                .hasValueSatisfying(outboxEventEntity -> {
                    assertThat(outboxEventEntity.getPublishedAt())
                            .isNotNull()
                            .isAfter(timeBeforePaymentCreate)
                            .isBefore(timeAfterEventConsumed);
                    assertThat(outboxEventEntity.getFailedAt()).isNull();
                    assertThat(capturedOutput.getOut())
                            .contains(CONSUMED_PAYMENT_EVENT.formatted(outboxEventEntity.getPayload()));
                });
    }

    @Test
    public void consumerShouldNotProcessEventSecondTimeWhenHandlingDuplicate(CapturedOutput capturedOutput) {
        // given
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, IDEMPOTENCY_KEY_VALUE);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when
        ResponseEntity<String> response = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
                httpRequest,
                String.class);
        assertThat(response.getBody()).isNotNull();
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(consumedEventStorageJpa.findAll()).isNotEmpty();
                });
        OffsetDateTime timeAfterEventSendFirstTime = OffsetDateTime.now().plusSeconds(1);
        List<OutboxEventEntity> outboxEventEntities = outboxStorageJpa.findAll();
        assertThat(outboxEventEntities).hasSize(1);
        UUID eventId = outboxEventEntities.getFirst().getEventId();
        UUID createdPaymentId = outboxEventEntities.getFirst().getPaymentId();
        Optional<ConsumedEventEntity> consumedEventOptional = consumedEventStorageJpa.findById(eventId);
        assertThat(consumedEventOptional).isNotEmpty();
        ConsumedEventEntity consumedEvent = consumedEventOptional.get();

        int updatedRows = jdbcTemplate.update(
                "UPDATE outbox_event SET published_at = null WHERE aggregate_id = ?",
                createdPaymentId);
        assertThat(updatedRows).isOne();

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(capturedOutput.getOut()).contains(CONSUMED_EVENT_NOT_PROCESSED);
                    assertThat(capturedOutput.getOut()).contains(CONSUMED_EVENT_ALREADY_EXIST_IN_DB.formatted(eventId));
                    // the consumer is limited to only consume event and perform (or not if duplicate) simple db save
                    // so waiting for logs informing 'Event already exist in db' and 'Payment event not processed'
                    // is simulation of case when duplicate was consumed and no business operations were performed
                });
        assertThat(consumedEventStorageJpa.findAll())
                .singleElement()
                .satisfies(consumedEventEntity -> {
                    assertThat(consumedEventEntity.getEventId()).isEqualTo(consumedEvent.getEventId());
                    assertThat(consumedEventEntity.getConsumedAt())
                            .isEqualTo(consumedEvent.getConsumedAt())
                            .isBefore(timeAfterEventSendFirstTime);
                });
    }


    private HttpHeaders getRequestHeaders(MediaType mediaType, String idempotencyKey) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(mediaType);
        httpHeaders.set(IDEMPOTENCY_KEY, idempotencyKey);
        httpHeaders.set(CORRELATION_ID_HEADER, UUID.randomUUID().toString());
        return httpHeaders;
    }

}
