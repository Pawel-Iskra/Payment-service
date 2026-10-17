package priv.home.paymentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.data.OutboxStorageJdbc;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
public class PaymentServiceIntegrationTest {

    private static final String SIMULATED_OUTBOX_FAILURE_MESSAGE = "Simulated outbox failure";
    private static final String POSTGRES_IMAGE = "postgres:18";
    private static final String IDEMPOTENCY_KEY_FIRST = "abc-123";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_COUNT_ALL_PAYMENT_SQL = "SELECT COUNT(*) FROM payment";
    private static final String SELECT_COUNT_ALL_OUTBOX_EVENT_SQL = "SELECT COUNT(*) FROM outbox_event";
    private static final String SELECT_PAYMENT_BY_IDEMPOTENCY_KEY_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  idempotency_key = ?""";
    private static final RowMapper<Payment> PAYMENT_ROW_MAPPER =
            (resultSet, rowNum) -> Payment.builder()
                    .paymentId(resultSet.getObject("payment_id", UUID.class))
                    .amount(resultSet.getBigDecimal("amount"))
                    .currency(resultSet.getString("currency"))
                    .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                    .build();
    private static final String SELECT_EVENT_BY_PAYMENT_ID_SQL = """
            SELECT payload FROM outbox_event
            WHERE aggregate_id = ?""";


    @Container
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private OutboxStorageJdbc outboxStorageJdbc;
    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    public void cleanTables() {
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM payment");
    }


    @Test
    public void shouldInsertBothPaymentAndEventToDbAsNormalExpectedCase() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentCreationDto resultPaymentCreationDto = paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, paymentRequest);

        // then
        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        assertThat(countEventRecords).isOne();
        assertThat(countPaymentRecords).isOne();

        Optional<Payment> paymentFromDbOptional = retrievePaymentByIdempotencyKey(IDEMPOTENCY_KEY_FIRST);
        assertThat(paymentFromDbOptional).isNotEmpty();
        Payment paymentFromDb = paymentFromDbOptional.get();

        List<String> eventPayloadFromDbAsList = retrieveEventPayloadByPaymentId(resultPaymentCreationDto.getPaymentId());
        assertThat(eventPayloadFromDbAsList).isNotEmpty();
        String eventPayload = eventPayloadFromDbAsList.getFirst();
        PaymentCreatedEvent eventFromPayload = generatePaymentCreatedEventFromPayloadString(eventPayload);

        compareValuesInPaymentAndInPaymentCreatedEvent(paymentFromDb, eventFromPayload);
        assertThat(eventFromPayload.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_FIRST);
    }

    @Test
    public void shouldInsertOnePaymentToDbWhenTwoSameRequests() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentCreationDto resultFirstInsert = paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, paymentRequest);
        PaymentCreationDto resultSecondInsert = paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, paymentRequest);

        // then
        assertThat(resultFirstInsert.getPaymentId()).isEqualTo(resultSecondInsert.getPaymentId());
        assertThat(resultFirstInsert.getAmount()).isEqualTo(resultSecondInsert.getAmount());
        assertThat(resultFirstInsert.getCurrency()).isEqualTo(resultSecondInsert.getCurrency());
        assertThat(resultFirstInsert.getPaymentStatus()).isEqualTo(resultSecondInsert.getPaymentStatus());
        assertThat(resultFirstInsert.isWasAlreadyInDb()).isFalse();
        assertThat(resultSecondInsert.isWasAlreadyInDb()).isTrue();

        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        assertThat(countEventRecords).isOne();
        assertThat(countPaymentRecords).isOne();

        Optional<Payment> paymentFromDbOptional = retrievePaymentByIdempotencyKey(IDEMPOTENCY_KEY_FIRST);
        assertThat(paymentFromDbOptional).isNotEmpty();
        Payment paymentFromDb = paymentFromDbOptional.get();

        List<String> eventPayloadFromDbAsList = retrieveEventPayloadByPaymentId(resultFirstInsert.getPaymentId());
        assertThat(eventPayloadFromDbAsList).isNotEmpty();
        String eventPayload = eventPayloadFromDbAsList.getFirst();
        PaymentCreatedEvent eventFromPayload = generatePaymentCreatedEventFromPayloadString(eventPayload);

        compareValuesInPaymentAndInPaymentCreatedEvent(paymentFromDb, eventFromPayload);
        assertThat(eventFromPayload.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_FIRST);
    }

    @Test
    public void shouldInsertOnePaymentToDbWhenRaceCondition() throws ExecutionException, InterruptedException {
        // given
        UUID paymentId;
        PaymentRequest paymentRequest = getValidPaymentRequest();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startThreadsForInsert = new CountDownLatch(1);

        Callable<PaymentCreationDto> insertPayment = () -> {
            startThreadsForInsert.await();
            return paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, paymentRequest);
        };

        // when + then
        try {
            Future<PaymentCreationDto> first = executor.submit(insertPayment);
            Future<PaymentCreationDto> second = executor.submit(insertPayment);
            startThreadsForInsert.countDown();

            PaymentCreationDto firstThreadResult = first.get();
            PaymentCreationDto secondThreadResult = second.get();

            assertThat(firstThreadResult.getPaymentId()).isEqualTo(secondThreadResult.getPaymentId());
            assertThat(firstThreadResult.isWasAlreadyInDb()).isNotEqualTo(secondThreadResult.isWasAlreadyInDb());
            paymentId = firstThreadResult.getPaymentId();
        } finally {
            executor.shutdownNow();
        }

        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        assertThat(countEventRecords).isOne();
        assertThat(countPaymentRecords).isOne();

        Optional<Payment> paymentFromDbOptional = retrievePaymentByIdempotencyKey(IDEMPOTENCY_KEY_FIRST);
        assertThat(paymentFromDbOptional).isNotEmpty();
        Payment paymentFromDb = paymentFromDbOptional.get();

        List<String> eventPayloadFromDbAsList = retrieveEventPayloadByPaymentId(paymentId);
        assertThat(eventPayloadFromDbAsList).isNotEmpty();
        String eventPayload = eventPayloadFromDbAsList.getFirst();
        PaymentCreatedEvent eventFromPayload = generatePaymentCreatedEventFromPayloadString(eventPayload);

        compareValuesInPaymentAndInPaymentCreatedEvent(paymentFromDb, eventFromPayload);
        assertThat(eventFromPayload.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_FIRST);
    }

    @Test
    public void shouldRollbackPaymentInsertIfOutboxInsertThrowsException() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        doThrow(new RuntimeException(SIMULATED_OUTBOX_FAILURE_MESSAGE)).when(outboxStorageJdbc)
                .addPaymentEventToOutbox(any(UUID.class), any(UUID.class), any(String.class), any(String.class));

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, paymentRequest));

        // then
        assertThat(thrown.getMessage()).contains(SIMULATED_OUTBOX_FAILURE_MESSAGE);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        assertThat(countPaymentRecords).isZero();
        assertThat(countEventRecords).isZero();
    }


    private void compareValuesInPaymentAndInPaymentCreatedEvent(Payment payment, PaymentCreatedEvent paymentCreatedEvent) {
        assertThat(payment.getPaymentId()).isEqualTo(paymentCreatedEvent.getPaymentId());
        assertThat(payment.getCurrency()).isEqualTo(paymentCreatedEvent.getCurrency());
        assertThat(payment.getAmount()).isEqualTo(paymentCreatedEvent.getAmount());
        assertThat(payment.getPaymentStatus()).isEqualTo(paymentCreatedEvent.getPaymentStatus());
    }

    private List<String> retrieveEventPayloadByPaymentId(UUID paymentId) {
        return jdbcTemplate.query(
                SELECT_EVENT_BY_PAYMENT_ID_SQL,
                (rs, rowNum) -> rs.getString("payload"),
                paymentId);
    }

    private Optional<Payment> retrievePaymentByIdempotencyKey(String idempotencyKey) {
        Optional<Payment> resultFromDb;
        try {
            resultFromDb = Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT_PAYMENT_BY_IDEMPOTENCY_KEY_SQL,
                    PAYMENT_ROW_MAPPER,
                    idempotencyKey));
        } catch (EmptyResultDataAccessException exception) {
            resultFromDb = Optional.empty();
        }
        return resultFromDb;
    }

    private PaymentCreatedEvent generatePaymentCreatedEventFromPayloadString(String payload) {
        return objectMapper.readValue(payload, PaymentCreatedEvent.class);
    }


    private PaymentRequest getValidPaymentRequest() {
        return new PaymentRequest(VALID_AMOUNT, CURRENCY_PLN);
    }

}
