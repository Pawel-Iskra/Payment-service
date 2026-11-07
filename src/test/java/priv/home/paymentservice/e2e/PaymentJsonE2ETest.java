package priv.home.paymentservice.e2e;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.api.errorhandling.ErrorResponse;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentJsonE2ETest extends AbstractE2ETest {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String IDEMPOTENCY_KEY_IS_REQUIRED = "Required header 'idempotency-key' is not present";
    private static final String IDEMPOTENCY_KEY_PATTERN_CONSTRAINT = "idempotency-key must start with a letter and contain 2-128 allowed characters";
    private static final String CURRENCY_MUST_RESPECT_PATTERN = "Currency code must consist of exactly 3 uppercase letters";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Amount must be greater than 0";
    private static final String AMOUNT_MUST_NOT_EXCEED = "Amount must not exceed 9999999999.99 and must have at most 2 decimal places";
    private static final String API_PATH = "/payments";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("payment-service.payment-event-publisher.scheduler.enabled", () -> false);
        // disable event-publisher scheduler in this test class
    }


    @Test
    public void shouldCreatePaymentThroughJsonEndpointAndAddPaymentAndEventToDb() {
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
                            .isAfterOrEqualTo(eventEntity.getCreatedAt())
                            .isBefore(timeAfterPaymentCreate.plus(calculateBackoff(newlyCreatedEventAttemptCount)));
                    assertThat(eventEntity.getEventType()).isEqualTo(PaymentEventType.PAYMENT_CREATED_EVENT.name());
                    assertThat(eventEntity.getAttemptCount()).isZero();
                    assertThat(eventEntity.getPayload()).isNotNull();
                    PaymentCreatedEvent paymentCreatedEventFromPayment = getPaymentCreatedEventFromPayment(
                            eventEntity.getEventId(), paymentFromDb);
                    PaymentCreatedEvent paymentCreatedEventFromEntityPayload = objectMapper
                            .readValue(eventEntity.getPayload(), PaymentCreatedEvent.class);
                    assertThat(paymentCreatedEventFromPayment).isEqualTo(paymentCreatedEventFromEntityPayload);
                });
    }

    @Test
    public void shouldReturnExistingPaymentForDuplicateIdempotencyKey() {
        // given
        BigDecimal amount = new BigDecimal("1500.50");
        String currency = "USD";
        String idempotencyKey = "abcd-1234";
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(amount, currency);
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, idempotencyKey);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);

        // when
        ResponseEntity<PaymentResponse> responseFirst = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                PaymentResponse.class);
        ResponseEntity<PaymentResponse> responseSecond = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                PaymentResponse.class);

        // then
        assertThat(responseSecond.getBody()).isNotNull();
        assertThat(responseFirst.getBody()).isNotNull();
        assertThat(responseFirst.getBody().paymentId()).isEqualTo(responseSecond.getBody().paymentId());
        assertThat(responseFirst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseSecond.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentStorageJpa.findAll()).hasSize(1);
        assertThat(outboxStorageJpa.findAll()).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    public void shouldReturnBadRequestWhenAmountIsNotCorrect(BigDecimal amount, String expectedMessage) {
        // given
        String idempotencyKey = "abcd-1234";
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(amount, "PLN");
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, idempotencyKey);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);
        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo(expectedMessage);
        assertThat(paymentStorageJpa.findAll()).isEmpty();
        assertThat(outboxStorageJpa.findAll()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "E", "EU", " EU", "USDD", "usd", "U$D", "UD1"})
    public void shouldReturnBadRequestWhenCurrencyIsNotCorrect(String currency) {
        // given
        String idempotencyKey = "abcd-1234";
        BigDecimal requestAmount = new BigDecimal("123.45");
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(requestAmount, currency);
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, idempotencyKey);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);
        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo(CURRENCY_MUST_RESPECT_PATTERN);
        assertThat(paymentStorageJpa.findAll()).isEmpty();
        assertThat(outboxStorageJpa.findAll()).isEmpty();
    }

    @Test
    public void shouldReturnBadRequestWhenHeaderIsMissing() {
        // given
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(new BigDecimal("123.45"), "PLN");
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, "abc-123");
        requestHeaders.remove(IDEMPOTENCY_KEY);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);
        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo(IDEMPOTENCY_KEY_IS_REQUIRED);
        assertThat(paymentStorageJpa.findAll()).isEmpty();
        assertThat(outboxStorageJpa.findAll()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("invalidIdempotencyKeys")
    public void shouldReturnBadRequestWhenHeaderIsNotCorrect(String idempotencyKey) {
        // given
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(new BigDecimal("123.45"), "PLN");
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, idempotencyKey);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);
        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains(IDEMPOTENCY_KEY_PATTERN_CONSTRAINT);
        assertThat(paymentStorageJpa.findAll()).isEmpty();
        assertThat(outboxStorageJpa.findAll()).isEmpty();
    }


    private static Stream<Arguments> invalidAmounts() {
        return Stream.of(
                Arguments.of(new BigDecimal("0.0"), AMOUNT_MUST_BE_GREATER_THAN_ZERO),
                Arguments.of(new BigDecimal("-19"), AMOUNT_MUST_BE_GREATER_THAN_ZERO),
                Arguments.of(new BigDecimal("123.456"), AMOUNT_MUST_NOT_EXCEED),
                Arguments.of(new BigDecimal("112233445566.00"), AMOUNT_MUST_NOT_EXCEED));
    }

    private static Stream<String> invalidIdempotencyKeys() {
        return Stream.of("", " ", "\t", "123",
                "a", "1ddfg", "-011", "4&5", "d-124%",
                getRandomString(129));
    }

    private static String getRandomString(int length) {
        StringBuilder stringBuilder = new StringBuilder();
        new Random()
                .ints(length, 'a', 'z' + 1)
                .forEach(stringBuilder::appendCodePoint);
        return stringBuilder.toString();
    }

    private CreatePaymentRequest getCreatePaymentRequest(BigDecimal amount, String currency) {
        return CreatePaymentRequest.builder()
                .amount(amount)
                .currency(currency)
                .build();
    }

    private HttpHeaders getRequestHeaders(MediaType mediaType, String idempotencyKey) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(mediaType);
        httpHeaders.set(IDEMPOTENCY_KEY, idempotencyKey);
        return httpHeaders;
    }

    private Duration calculateBackoff(int attemptsDone) {
        OutboxEventPublisherProperties.Retry retryProperties = publisherProperties.retry();
        int multiplier = Math.powExact(retryProperties.multiplier(), attemptsDone);
        Duration calculatedBackoff = retryProperties.initialBackoff().multipliedBy(multiplier);
        return calculatedBackoff.compareTo(retryProperties.maxBackoff()) > 0 ?
                retryProperties.maxBackoff() : calculatedBackoff;
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(UUID eventId, PaymentEntity paymentToPublish) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentToPublish.getPaymentId())
                .eventId(eventId)
                .amount(paymentToPublish.getAmount())
                .currency(paymentToPublish.getCurrency())
                .paymentStatus(paymentToPublish.getPaymentStatus())
                .idempotencyKey(paymentToPublish.getIdempotencyKey())
                .build();
    }
}
