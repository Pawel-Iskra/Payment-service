package priv.home.paymentservice.payment.application;

import iso.std.iso._20022.tech.xsd.pain_002_001.CustomerPaymentStatusReportV14;
import iso.std.iso._20022.tech.xsd.pain_002_001.GroupHeader128;
import iso.std.iso._20022.tech.xsd.pain_002_001.OriginalGroupHeader22;
import iso.std.iso._20022.tech.xsd.pain_002_001.OriginalPaymentInstruction51;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.xml.sax.SAXParseException;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.iso20022.Pain001TestData;
import priv.home.paymentservice.payment.iso20022.pain001.InvalidPain001XmlException;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class CreatePaymentPain001ServiceIntegrationTest {

    // expected values from pain001-single-payment.xml
    private static final String GROUP_HEADER_MESSAGE_ID = "MSG-20261027-001";
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("125.50");
    private static final String PAYMENT_CURRENCY = "EUR";
    private static final String PAYMENT_INFO_001 = "PMT-INF-001";
    private static final String PAYMENT_ID_END_TO_END = "E2E-20261027-0001";

    private static final String POSTGRES_DOCKER_IMAGE = "postgres:18";
    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final String PAIN002_STATUS_REPORT_PREFIX = "S-";
    private static final String PAIN001_VERSION = "pain.001.001.12";
    private static final String PAIN001_XML_IS_MALFORMED =
            "Pain.001 XML is malformed or does not match the supported profile";

    @Container
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_DOCKER_IMAGE);

    @DynamicPropertySource
    static void configurePostgresContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PaymentStorageJpa paymentStorageJpa;
    @MockitoSpyBean
    private OutboxStorageJpa outboxStorageJpa;

    @Autowired
    private CreatePaymentPain001Service createPaymentPain001Service;


    @BeforeEach
    public void cleanTables() {
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM payment");
    }

    @Test
    public void shouldAddPaymentAndEventToDbAndReturnProperValuesForCreatePain001Flow() {
        // given
        OffsetDateTime timeBeforePaymentCreate = OffsetDateTime.now().minusSeconds(1);
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        byte[] pain001XmlStringBytes = pain001XmlString.getBytes(StandardCharsets.UTF_8);

        // when
        CreatePain001Result createPain001Result = createPaymentPain001Service
                .createPaymentFromPain001Xml(IDEMPOTENCY_KEY_VALUE, pain001XmlStringBytes);

        // then
        assertThat(createPain001Result.wasAlreadyInDb()).isFalse();
        UUID resultPaymentId = createPain001Result.paymentId();
        iso.std.iso._20022.tech.xsd.pain_002_001.Document resultPain002StatusReport = createPain001Result
                .createPain001PaymentStatusReportDocument();
        assertThat(resultPain002StatusReport.getCstmrPmtStsRpt()).isNotNull();
        CustomerPaymentStatusReportV14 statusReportV14 = resultPain002StatusReport.getCstmrPmtStsRpt();
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

        Optional<PaymentEntity> paymentEntityOptional = paymentStorageJpa.findById(resultPaymentId);
        assertThat(paymentEntityOptional)
                .hasValueSatisfying(paymentEntity -> {
                    assertThat(paymentEntity.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
                    assertThat(paymentEntity.getAmount()).isEqualTo(PAYMENT_AMOUNT);
                    assertThat(paymentEntity.getCurrency()).isEqualTo(PAYMENT_CURRENCY);
                    assertThat(paymentEntity.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_VALUE);
                });

        OffsetDateTime timeAfterPaymentCreate = OffsetDateTime.now().plusSeconds(1);
        List<OutboxEventEntity> eventEntityListFromDb = outboxStorageJpa.findAll();
        assertThat(eventEntityListFromDb)
                .singleElement()
                .satisfies(eventEntity -> {
                    assertThat(eventEntity.getPaymentId()).isEqualTo(resultPaymentId);
                    assertThat(eventEntity.getEventId())
                            .isNotNull()
                            .isInstanceOf(UUID.class);
                    assertThat(eventEntity.getCreatedAt())
                            .isAfter(timeBeforePaymentCreate)
                            .isBefore(timeAfterPaymentCreate);
                    assertThat(eventEntity.getFailedAt()).isNull();
                    assertThat(eventEntity.getPublishedAt()).isNull();
                    assertThat(eventEntity.getNextAttemptAt()).isNotNull();
                    assertThat(eventEntity.getLastError()).isNull();
                    assertThat(eventEntity.getEventType()).isEqualTo(PaymentEventType.PAYMENT_CREATED_EVENT.name());
                    assertThat(eventEntity.getAttemptCount()).isZero();
                    assertThat(eventEntity.getPayload()).isNotNull();
                });
    }

    @Test
    public void shouldHandleAddingSamePaymentTwice() {
        // given
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        byte[] pain001XmlStringBytes = pain001XmlString.getBytes(StandardCharsets.UTF_8);

        // when
        CreatePain001Result createPain001ResultFirst = createPaymentPain001Service
                .createPaymentFromPain001Xml(IDEMPOTENCY_KEY_VALUE, pain001XmlStringBytes);
        CreatePain001Result createPain001ResultSecond = createPaymentPain001Service
                .createPaymentFromPain001Xml(IDEMPOTENCY_KEY_VALUE, pain001XmlStringBytes);

        // then
        assertThat(createPain001ResultFirst.paymentId()).isEqualTo(createPain001ResultSecond.paymentId());
        assertThat(createPain001ResultFirst.wasAlreadyInDb()).isFalse();
        assertThat(createPain001ResultSecond.wasAlreadyInDb()).isTrue();
        UUID resultPaymentId = createPain001ResultFirst.paymentId();
        List<PaymentEntity> paymentListFromDb = paymentStorageJpa.findAll();
        List<OutboxEventEntity> eventEntityListFromDb = outboxStorageJpa.findAll();
        assertThat(paymentListFromDb)
                .isNotEmpty()
                .singleElement()
                .satisfies(paymentEntity -> {
                    assertThat(paymentEntity.getPaymentId()).isEqualTo(resultPaymentId);
                });
        assertThat(eventEntityListFromDb)
                .isNotEmpty()
                .singleElement()
                .satisfies(eventEntity -> {
                    assertThat(eventEntity.getPaymentId()).isEqualTo(resultPaymentId);
                });
    }

    @Test
    public void shouldThrowExceptionWhenNotCorrectInputXml() {
        // given
        byte[] pain001XmlStringBytes = "<Document/>".getBytes(StandardCharsets.UTF_8);

        // when + then
        assertThatThrownBy(() -> createPaymentPain001Service
                .createPaymentFromPain001Xml(IDEMPOTENCY_KEY_VALUE, pain001XmlStringBytes))
                .isInstanceOf(InvalidPain001XmlException.class)
                .hasMessageContaining(PAIN001_XML_IS_MALFORMED)
                .hasStackTraceContaining(SAXParseException.class.getName());
        assertThat(paymentStorageJpa.findAll()).isEmpty();
        assertThat(outboxStorageJpa.findAll()).isEmpty();
    }

    @Test
    public void shouldRollbackPaymentWhenOutboxEventStorageException() {
        // given
        String errorMessage = "Simulated outboxStorage failure";
        String pain001XmlString = Pain001TestData.getPain001SinglePaymentCorrectXmlString();
        byte[] pain001XmlStringBytes = pain001XmlString.getBytes(StandardCharsets.UTF_8);
        doThrow(new RuntimeException(errorMessage))
                .when(outboxStorageJpa).save(any(OutboxEventEntity.class));

        // when + then
        assertThatThrownBy(() -> createPaymentPain001Service
                .createPaymentFromPain001Xml(IDEMPOTENCY_KEY_VALUE, pain001XmlStringBytes))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(errorMessage);

        verify(outboxStorageJpa, times(1)).save(any(OutboxEventEntity.class));
        assertThat(paymentStorageJpa.findAll()).isEmpty();
        assertThat(outboxStorageJpa.findAll()).isEmpty();
    }

    // TODO: test for concurrent payment creation

}