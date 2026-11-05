package priv.home.paymentservice.e2e;


import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentJsonE2ETest extends AbstractE2ETest {

    private static final String IDEMPOTENCY_KEY_NAME = "idempotency-key";
    private static final String API_PATH = "/payments";


    @Test
    public void shouldCreatePaymentThroughJsonEndpoint() {
        // given
        OffsetDateTime timeBeforePaymentCreate = OffsetDateTime.now().minusSeconds(1);
        int newlyCreatedEventAttemptCount = 0;
        BigDecimal amount = new BigDecimal("1500.50");
        String currency = "USD";
        String idempotencyKey = "abcd-1234";
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(amount, currency);
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, idempotencyKey);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);

        // when
        ResponseEntity<PaymentResponse> response = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                PaymentResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        UUID paymentIdFromResponse = response.getBody().paymentId();
        Optional<PaymentEntity> paymentFromDbOptional = paymentStorageJpa.findById(paymentIdFromResponse);
        PaymentEntity paymentFromDb = assertThat(paymentFromDbOptional)
                .isPresent()
                .actual()
                .get();
        assertThat(paymentFromDb)
                .satisfies(paymentEntity -> {
                    assertThat(paymentEntity.getPaymentId()).isEqualTo(paymentIdFromResponse);
                    assertThat(paymentEntity.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
                    assertThat(paymentEntity.getAmount()).isEqualTo(amount);
                    assertThat(paymentEntity.getCurrency()).isEqualTo(currency);
                    assertThat(paymentEntity.getIdempotencyKey()).isEqualTo(idempotencyKey);
                });
        List<OutboxEventEntity> outboxEventEntityList = outboxStorageJpa.findAll();
        OffsetDateTime timeAfterPaymentCreate = OffsetDateTime.now().plusSeconds(1);
        assertThat(outboxEventEntityList)
                .singleElement()
                .satisfies(eventEntity -> {
                    assertThat(eventEntity.getPaymentId()).isEqualTo(paymentIdFromResponse);
                    assertThat(eventEntity.getEventId())
                            .isNotNull()
                            .isInstanceOf(UUID.class);
                    assertThat(eventEntity.getCreatedAt())
                            .isAfter(timeBeforePaymentCreate)
                            .isBefore(timeAfterPaymentCreate);
                    assertThat(eventEntity.getFailedAt()).isNull();
                    assertThat(eventEntity.getPublishedAt()).isNull();
                    assertThat(eventEntity.getNextAttemptAt()).isNotNull();
                    assertThat(eventEntity.getNextAttemptAt())
                            .isAfter(timeBeforePaymentCreate)
                            .isBefore(timeAfterPaymentCreate.plus(calculateBackoff(newlyCreatedEventAttemptCount)));
                    assertThat(eventEntity.getEventType()).isEqualTo(PaymentEventType.PAYMENT_CREATED_EVENT.name());
                    assertThat(eventEntity.getAttemptCount()).isZero();
                    assertThat(eventEntity.getPayload()).isNotNull();
                    PaymentCreatedEvent paymentCreatedEvent = getPaymentCreatedEventFromPayment(
                            eventEntity.getEventId(), idempotencyKey, paymentFromDb);
//                    assertThat(objectMapper.readValue(eventEntity.getPayload(), PaymentCreatedEvent.class))
//                            .isEqualTo(paymentCreatedEvent); // tODO: field by field
                });
    }

    // TODO: separate test to send http request -> check KafkaEvent
    //  by KafkaConsumer without again checking db


    private CreatePaymentRequest getCreatePaymentRequest(BigDecimal amount, String currency) {
        return CreatePaymentRequest.builder()
                .amount(amount)
                .currency(currency)
                .build();
    }

    private HttpHeaders getRequestHeaders(MediaType mediaType, String idempotencyKey) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(mediaType);
        httpHeaders.set(IDEMPOTENCY_KEY_NAME, idempotencyKey);
        return httpHeaders;
    }

    private Duration calculateBackoff(int attemptsDone) {
        OutboxEventPublisherProperties.Retry retryProperties = publisherProperties.retry();
        int multiplier = Math.powExact(retryProperties.multiplier(), attemptsDone);
        Duration calculatedBackoff = retryProperties.initialBackoff().multipliedBy(multiplier);
        return calculatedBackoff.compareTo(retryProperties.maxBackoff()) > 0 ?
                retryProperties.maxBackoff() : calculatedBackoff;
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(UUID eventId, String idempotencyKey, PaymentEntity paymentToPublish) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentToPublish.getPaymentId())
                .eventId(eventId)
                .amount(paymentToPublish.getAmount())
                .currency(paymentToPublish.getCurrency())
                .paymentStatus(paymentToPublish.getPaymentStatus())
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
