package priv.home.paymentservice.e2e;

import iso.std.iso._20022.tech.xsd.pain_002_001.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.payment.api.errorhandling.ErrorResponse;
import priv.home.paymentservice.payment.iso20022.Pain001TestData;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class Pain001XmlE2ETest extends AbstractE2ETest {

    // expected values from pain001-single-payment.xml
    private static final String GROUP_HEADER_MESSAGE_ID = "MSG-20261027-001";
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("125.50");
    private static final String PAYMENT_CURRENCY = "EUR";
    private static final String PAYMENT_INFO_001 = "PMT-INF-001";
    private static final String PAYMENT_ID_END_TO_END = "E2E-20261027-0001";

    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final String PAIN002_STATUS_REPORT_PREFIX = "S-";
    private static final String PAIN001_VERSION = "pain.001.001.12";
    private static final String PAIN001_XML_POST_PATH = "/payments/pain001";
    private static final String IDEMPOTENCY_KEY_IS_REQUIRED = "Required header 'idempotency-key' is not present";
    private static final String IDEMPOTENCY_KEY_PATTERN_CONSTRAINT = "idempotency-key must start with a letter and contain 2-128 allowed characters";
    private static final String CURRENCY_MUST_RESPECT_PATTERN = "Currency code must consist of exactly 3 uppercase letters";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Amount must be greater than 0";
    private static final String AMOUNT_MUST_NOT_EXCEED = "Amount must not exceed 9999999999.99 and must have at most 2 decimal places";


    @Test
    public void shouldCreatePaymentThroughXmlEndpointAndAddPaymentAndEventToDb() throws JAXBException {
        // given
        OffsetDateTime timeBeforePaymentCreate = OffsetDateTime.now().minusSeconds(1);
        int newlyCreatedEventAttemptCount = 0;
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<Document> pain002ResponseResult = unmarshaller.unmarshal(
                new StreamSource(new StringReader(response.getBody())), Document.class);
        assertThat(pain002ResponseResult.getValue().getCstmrPmtStsRpt()).isNotNull();
        CustomerPaymentStatusReportV14 statusReportV14 = pain002ResponseResult.getValue().getCstmrPmtStsRpt();
        assertThat(statusReportV14.getGrpHdr()).isNotNull();
        GroupHeader128 groupHeader128 = statusReportV14.getGrpHdr();
        assertThat(groupHeader128.getMsgId()).startsWith(PAIN002_STATUS_REPORT_PREFIX);
        assertThat(statusReportV14.getOrgnlGrpInfAndSts()).isNotNull();
        OriginalGroupHeader22 groupHeader22 = statusReportV14.getOrgnlGrpInfAndSts();
        assertThat(groupHeader22.getOrgnlMsgId()).isEqualTo(GROUP_HEADER_MESSAGE_ID);
        assertThat(groupHeader22.getOrgnlMsgNmId()).isEqualTo(PAIN001_VERSION);
        assertThat(statusReportV14.getOrgnlPmtInfAndSts()).isNotEmpty();
        OriginalPaymentInstruction51 instruction51 = statusReportV14.getOrgnlPmtInfAndSts().getFirst();
        assertThat(instruction51).isNotNull();
        assertThat(instruction51.getOrgnlPmtInfId()).isEqualTo(PAYMENT_INFO_001);
        assertThat(instruction51.getTxInfAndSts()).isNotEmpty();
        assertThat(instruction51.getTxInfAndSts().getFirst().getOrgnlEndToEndId()).isEqualTo(PAYMENT_ID_END_TO_END);


        List<PaymentEntity> paymentListFromDb = paymentStorageJpa.findAll();
        assertThat(paymentListFromDb).hasSize(1);
        PaymentEntity paymentFromDb = paymentListFromDb.getFirst();
        UUID paymentId = paymentFromDb.getPaymentId();
        assertThat(paymentFromDb)
                .satisfies(paymentEntity -> {
                    assertThat(paymentEntity.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
                    assertThat(paymentEntity.getAmount()).isEqualTo(PAYMENT_AMOUNT);
                    assertThat(paymentEntity.getCurrency()).isEqualTo(PAYMENT_CURRENCY);
                    assertThat(paymentEntity.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_VALUE);
                });
        List<OutboxEventEntity> outboxEventEntityList = outboxStorageJpa.findAll();
        OffsetDateTime timeAfterPaymentCreate = OffsetDateTime.now().plusSeconds(1);
        assertThat(outboxEventEntityList)
                .singleElement()
                .satisfies(eventEntity -> {
                    assertThat(eventEntity.getPaymentId()).isEqualTo(paymentId);
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
        //TODO: checking data from paymentEntity, outboxEntity could be common for Json and Xml E2E tests
    }

    @Test
    public void shouldReturnOKForDuplicateIdempotencyKeyAndNotPersistSecondTime() {
        // given
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, IDEMPOTENCY_KEY_VALUE);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when + then
        ResponseEntity<String> responseFirst = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
                httpRequest,
                String.class);
        assertThat(responseFirst.getBody()).isNotNull();
        assertThat(responseFirst.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<PaymentEntity> paymentListFromDb = paymentStorageJpa.findAll();
        assertThat(paymentListFromDb).hasSize(1);
        PaymentEntity paymentFromDb = paymentListFromDb.getFirst();
        UUID paymentId = paymentFromDb.getPaymentId();
        assertThat(outboxStorageJpa.findAll())
                .singleElement()
                .satisfies(outboxEventEntity -> {
                    assertThat(outboxEventEntity.getPaymentId()).isEqualTo(paymentId);
                });


        ResponseEntity<String> responseSecond = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
                httpRequest,
                String.class);
        assertThat(responseFirst.getBody()).isNotNull();
        assertThat(responseSecond.getStatusCode()).isEqualTo(HttpStatus.OK);

        paymentListFromDb = paymentStorageJpa.findAll();
        assertThat(paymentListFromDb)
                .singleElement()
                .satisfies(paymentEntity -> {
                    assertThat(paymentEntity.getPaymentId()).isEqualTo(paymentId);
                });
        assertThat(outboxStorageJpa.findAll())
                .singleElement()
                .satisfies(outboxEventEntity -> {
                    assertThat(outboxEventEntity.getPaymentId()).isEqualTo(paymentId);
                });
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    public void shouldReturnBadRequestWhenAmountIsNotCorrect(BigDecimal amount, String expectedMessage) {
        // given
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString(amount.toString(), "PLN");
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, IDEMPOTENCY_KEY_VALUE);
        requestHeaders.set("Accept", MediaType.APPLICATION_XML_VALUE);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
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
        String amount = "14750.45";
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString(amount, currency);
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, IDEMPOTENCY_KEY_VALUE);
        requestHeaders.set("Accept", MediaType.APPLICATION_XML_VALUE);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
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
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, IDEMPOTENCY_KEY_VALUE);
        requestHeaders.set("Accept", MediaType.APPLICATION_XML_VALUE);
        requestHeaders.remove(IDEMPOTENCY_KEY);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
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
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_XML, idempotencyKey);
        requestHeaders.set("Accept", MediaType.APPLICATION_XML_VALUE);
        HttpEntity<byte[]> httpRequest = new HttpEntity<>(pain001XmlString.getBytes(), requestHeaders);

        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.exchange(
                PAIN001_XML_POST_PATH,
                HttpMethod.POST,
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

}
