package priv.home.paymentservice.eventconsumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.eventconsumer.persistence.ConsumedEventEntity;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.payment.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class PaymentEventConsumerServiceIntegrationTest {


    private static final String POSTGRES_DOCKER_IMAGE = "postgres:18";
    private static final String EVENT_ACCEPTED_FOR_PROCESSING_LOG_MESSAGE = "Event accepted for business processing, eventId=%s";
    private static final String EVENT_ALREADY_EXIST_IN_DB_LOG_MESSAGE = "Event already exist in db, eventId=%s";
    private static final String SELECT_ALL_FROM_CONSUMED_EVENT_TABLE = """
            SELECT event_id, consumed_at
            FROM consumed_event
            """;


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
    private PaymentEventConsumerService underTest;

    @BeforeEach
    public void cleanTable() {
        jdbcTemplate.update("DELETE FROM consumed_event");
    }

    @Test
    public void shouldSuccessfullyProcessGivenPayload(CapturedOutput output) {
        // given
        UUID eventId = generateUuid();
        PaymentCreatedEvent paymentCreatedEvent = getValidPaymentCreatedEvent(eventId);
        String payload = getValidEventPayload(paymentCreatedEvent);

        // when
        Optional<ConsumedEventEntity> eventResultOptional = underTest.processConsumedEvent(payload);

        // then
        assertThat(eventResultOptional)
                .hasValueSatisfying(eventResult -> {
                    assertThat(eventResult.getEventId()).isEqualTo(eventId);
                    assertThat(eventResult.getConsumedAt()).isNotNull();
                });
        assertThat(output).contains(EVENT_ACCEPTED_FOR_PROCESSING_LOG_MESSAGE.formatted(eventId));

        List<ConsumedEventEntity> recordsFromDb = retrieveAllRecordsFromConsumedEventTableWithJdbc();
        assertThat(recordsFromDb)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getEventId()).isEqualTo(eventId);
                    assertThat(record.getConsumedAt()).isNotNull();
                });
    }

    @Test
    public void shouldLogEventAlreadyExistAndReturnEmptyOptional(CapturedOutput output) {
        // given
        UUID eventId = generateUuid();
        PaymentCreatedEvent paymentCreatedEvent = getValidPaymentCreatedEvent(eventId);
        String payload = getValidEventPayload(paymentCreatedEvent);
        jdbcTemplate.update("INSERT INTO consumed_event (event_id) VALUES (?)", eventId);

        // when
        Optional<ConsumedEventEntity> eventResultOptional = underTest.processConsumedEvent(payload);

        // then
        assertThat(eventResultOptional).isEmpty();
        assertThat(output).contains(EVENT_ALREADY_EXIST_IN_DB_LOG_MESSAGE.formatted(eventId));

        List<ConsumedEventEntity> recordsFromDb = retrieveAllRecordsFromConsumedEventTableWithJdbc();
        assertThat(recordsFromDb)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getEventId()).isEqualTo(eventId);
                    assertThat(record.getConsumedAt()).isNotNull();
                });
    }


    @Test
    public void shouldProcessOnceAndLogBothMessagesForRaceCondition(CapturedOutput output) throws InterruptedException, ExecutionException, TimeoutException {
        // given
        UUID eventId = generateUuid();
        PaymentCreatedEvent paymentCreatedEvent = getValidPaymentCreatedEvent(eventId);
        String payload = getValidEventPayload(paymentCreatedEvent);
        int threads = 2;

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        Callable<Optional<ConsumedEventEntity>> processPayload = () -> {
            readyLatch.countDown();
            startLatch.await();
            return underTest.processConsumedEvent(payload);
        };

        // when + then
        try {
            Future<Optional<ConsumedEventEntity>> firstThread = executorService.submit(processPayload);
            Future<Optional<ConsumedEventEntity>> secondThread = executorService.submit(processPayload);

            assertThat(readyLatch.await(4, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            Optional<ConsumedEventEntity> firstThreadResult = firstThread.get(4, TimeUnit.SECONDS);
            Optional<ConsumedEventEntity> secondThreadResult = secondThread.get(4, TimeUnit.SECONDS);

            assertThat(firstThreadResult.isPresent()).isNotEqualTo(secondThreadResult.isPresent());
            ConsumedEventEntity eventResult = firstThreadResult.orElseGet(secondThreadResult::get);
            assertThat(eventResult.getEventId()).isEqualTo(eventId);
            assertThat(eventResult.getConsumedAt()).isNotNull();
        } finally {
            startLatch.countDown();
            executorService.shutdownNow();
        }
        assertThat(output).contains(EVENT_ACCEPTED_FOR_PROCESSING_LOG_MESSAGE.formatted(eventId));
        assertThat(output).contains(EVENT_ALREADY_EXIST_IN_DB_LOG_MESSAGE.formatted(eventId));

        List<ConsumedEventEntity> recordsFromDb = retrieveAllRecordsFromConsumedEventTableWithJdbc();
        assertThat(recordsFromDb)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getEventId()).isEqualTo(eventId);
                    assertThat(record.getConsumedAt()).isNotNull();
                });
    }


    // event which is consumed is JsonString from PaymentCreatedEvent
    private String getValidEventPayload(PaymentCreatedEvent paymentCreatedEvent) {
        ObjectMapper objectMapperForMethod = new ObjectMapper();
        return objectMapperForMethod.writeValueAsString(paymentCreatedEvent);
    }

    private PaymentCreatedEvent getValidPaymentCreatedEvent(UUID eventId) {
        return PaymentCreatedEvent.builder()
                .eventId(eventId)
                .paymentId(generateUuid())
                .amount(new BigDecimal("123.45"))
                .currency("PLN")
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey("abc-123")
                .build();
    }

    private List<ConsumedEventEntity> retrieveAllRecordsFromConsumedEventTableWithJdbc() {
        return jdbcTemplate.query(
                SELECT_ALL_FROM_CONSUMED_EVENT_TABLE,
                (resultSet, rowNum) -> ConsumedEventEntity.builder()
                        .eventId(resultSet.getObject("event_id", UUID.class))
                        .consumedAt(resultSet.getObject("consumed_at", OffsetDateTime.class))
                        .build());
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}