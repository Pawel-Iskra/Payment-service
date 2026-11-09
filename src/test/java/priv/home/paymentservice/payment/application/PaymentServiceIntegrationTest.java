package priv.home.paymentservice.payment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import priv.home.paymentservice.integration.AbstractIntegrationTest;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.CreditorRequest;
import priv.home.paymentservice.payment.api.dto.DebtorRequest;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
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

public class PaymentServiceIntegrationTest extends AbstractIntegrationTest {


    private static final String END_TO_END_ID = "E2E-20261108-001";
    private static final String DEBTOR_IBAN = "PL93109024023152899461927776";
    private static final String CREDITOR_IBAN = "PL82109024029148361863856384";
    private static final DebtorRequest DEBTOR_REQUEST = new DebtorRequest("Debtor Name", DEBTOR_IBAN);
    private static final CreditorRequest CREDITOR_REQUEST = new CreditorRequest("Creditor Name", CREDITOR_IBAN);
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String IDEMPOTENCY_KEY_FIRST = "abc-123";
    private static final String SIMULATED_OUTBOX_FAILURE_MESSAGE = "Simulated outbox failure";
    private static final String SELECT_COUNT_ALL_PAYMENT_SQL = "SELECT COUNT(*) FROM payment";
    private static final String SELECT_COUNT_ALL_OUTBOX_EVENT_SQL = "SELECT COUNT(*) FROM outbox_event";
    private static final String SELECT_PAYMENT_BY_IDEMPOTENCY_KEY_SQL = """
            SELECT payment_id, amount, currency, payment_status, idempotency_key, end_to_end_id, debtor_iban, creditor_iban
            FROM payment
            WHERE  idempotency_key = ?""";
    private static final RowMapper<PaymentEntity> PAYMENT_ROW_MAPPER =
            (resultSet, rowNum) -> PaymentEntity.builder()
                    .paymentId(resultSet.getObject("payment_id", UUID.class))
                    .endToEndId(resultSet.getString("end_to_end_id"))
                    .debtorIban(resultSet.getString("debtor_iban"))
                    .creditorIban(resultSet.getString("creditor_iban"))
                    .amount(resultSet.getBigDecimal("amount"))
                    .currency(resultSet.getString("currency"))
                    .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                    .idempotencyKey(resultSet.getString("idempotency_key"))
                    .build();
    private static final String SELECT_EVENT_BY_PAYMENT_ID_SQL = """
            SELECT payload FROM outbox_event
            WHERE aggregate_id = ?""";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private OutboxStorageJpa outboxStorageJpa;
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
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();

        // when
        CreatePaymentResult resultCreatePaymentResult = paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, createPaymentRequest);

        // then
        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        assertThat(countEventRecords).isOne();
        assertThat(countPaymentRecords).isOne();

        Optional<PaymentEntity> paymentFromDbOptional = retrievePaymentByIdempotencyKey(IDEMPOTENCY_KEY_FIRST);
        assertThat(paymentFromDbOptional).isNotEmpty();
        PaymentEntity paymentFromDb = paymentFromDbOptional.get();

        List<String> eventPayloadFromDbAsList = retrieveEventPayloadByPaymentId(resultCreatePaymentResult.paymentId());
        assertThat(eventPayloadFromDbAsList).isNotEmpty();
        String eventPayload = eventPayloadFromDbAsList.getFirst();
        PaymentCreatedEvent eventFromPayload = generatePaymentCreatedEventFromPayloadString(eventPayload);

        compareValuesInPaymentAndInPaymentCreatedEvent(paymentFromDb, eventFromPayload);
        assertThat(eventFromPayload.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_FIRST);
    }

    @Test
    public void shouldInsertOnePaymentToDbWhenTwoSameRequests() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();

        // when
        CreatePaymentResult resultFirstInsert = paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, createPaymentRequest);
        CreatePaymentResult resultSecondInsert = paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, createPaymentRequest);

        // then
        assertThat(resultFirstInsert.paymentId()).isEqualTo(resultSecondInsert.paymentId());
        assertThat(resultFirstInsert.amount()).isEqualTo(resultSecondInsert.amount());
        assertThat(resultFirstInsert.currency()).isEqualTo(resultSecondInsert.currency());
        assertThat(resultFirstInsert.paymentStatus()).isEqualTo(resultSecondInsert.paymentStatus());
        assertThat(resultFirstInsert.wasAlreadyInDb()).isFalse();
        assertThat(resultSecondInsert.wasAlreadyInDb()).isTrue();

        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        assertThat(countEventRecords).isOne();
        assertThat(countPaymentRecords).isOne();

        Optional<PaymentEntity> paymentFromDbOptional = retrievePaymentByIdempotencyKey(IDEMPOTENCY_KEY_FIRST);
        assertThat(paymentFromDbOptional).isNotEmpty();
        PaymentEntity paymentFromDb = paymentFromDbOptional.get();

        List<String> eventPayloadFromDbAsList = retrieveEventPayloadByPaymentId(resultFirstInsert.paymentId());
        assertThat(eventPayloadFromDbAsList).isNotEmpty();
        String eventPayload = eventPayloadFromDbAsList.getFirst();
        PaymentCreatedEvent eventFromPayload = generatePaymentCreatedEventFromPayloadString(eventPayload);

        compareValuesInPaymentAndInPaymentCreatedEvent(paymentFromDb, eventFromPayload);
        assertThat(eventFromPayload.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_FIRST);
    }

    @Test
    public void shouldInsertOnePaymentToDbWhenRaceCondition() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        int threads = 2;
        int timeoutSec = 5;
        UUID paymentId;
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<CreatePaymentResult> insertPayment = () -> {
            readyLatch.countDown();
            startLatch.await();
            return paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, createPaymentRequest);
        };

        // when + then
        try {
            Future<CreatePaymentResult> first = executor.submit(insertPayment);
            Future<CreatePaymentResult> second = executor.submit(insertPayment);

            assertThat(readyLatch.await(timeoutSec, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            CreatePaymentResult firstThreadResult = first.get(timeoutSec, TimeUnit.SECONDS);
            CreatePaymentResult secondThreadResult = second.get(timeoutSec, TimeUnit.SECONDS);

            assertThat(firstThreadResult.paymentId()).isEqualTo(secondThreadResult.paymentId());
            assertThat(firstThreadResult.wasAlreadyInDb()).isNotEqualTo(secondThreadResult.wasAlreadyInDb());
            paymentId = firstThreadResult.paymentId();
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }

        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        assertThat(countEventRecords).isOne();
        assertThat(countPaymentRecords).isOne();

        Optional<PaymentEntity> paymentFromDbOptional = retrievePaymentByIdempotencyKey(IDEMPOTENCY_KEY_FIRST);
        assertThat(paymentFromDbOptional).isNotEmpty();
        PaymentEntity paymentFromDb = paymentFromDbOptional.get();

        List<String> eventPayloadFromDbAsList = retrieveEventPayloadByPaymentId(paymentId);
        assertThat(eventPayloadFromDbAsList).isNotEmpty();
        String eventPayload = eventPayloadFromDbAsList.getFirst();
        PaymentCreatedEvent eventFromPayload = generatePaymentCreatedEventFromPayloadString(eventPayload);

        compareValuesInPaymentAndInPaymentCreatedEvent(paymentFromDb, eventFromPayload);
        assertThat(eventFromPayload.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY_FIRST);
    }

    @Test
    public void shouldRollbackPaymentInsertIfOutboxInsertThrowsException() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        doThrow(new RuntimeException(SIMULATED_OUTBOX_FAILURE_MESSAGE))
                .when(outboxStorageJpa).save(any(OutboxEventEntity.class));

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, createPaymentRequest));

        // then
        assertThat(thrown.getMessage()).contains(SIMULATED_OUTBOX_FAILURE_MESSAGE);
        Integer countPaymentRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_PAYMENT_SQL, Integer.class);
        Integer countEventRecords = jdbcTemplate.queryForObject(SELECT_COUNT_ALL_OUTBOX_EVENT_SQL, Integer.class);
        assertThat(countPaymentRecords).isZero();
        assertThat(countEventRecords).isZero();
    }


    private void compareValuesInPaymentAndInPaymentCreatedEvent(PaymentEntity payment, PaymentCreatedEvent paymentCreatedEvent) {
        assertThat(payment.getPaymentId()).isEqualTo(paymentCreatedEvent.paymentId());
        assertThat(payment.getCurrency()).isEqualTo(paymentCreatedEvent.currency());
        assertThat(payment.getAmount()).isEqualTo(paymentCreatedEvent.amount());
        assertThat(payment.getPaymentStatus()).isEqualTo(paymentCreatedEvent.paymentStatus());
        assertThat(payment.getEndToEndId()).isEqualTo(paymentCreatedEvent.endToEndId());
        assertThat(payment.getDebtorIban()).isEqualTo(paymentCreatedEvent.debtorIban());
        assertThat(payment.getCreditorIban()).isEqualTo(paymentCreatedEvent.creditorIban());
    }

    private List<String> retrieveEventPayloadByPaymentId(UUID paymentId) {
        return jdbcTemplate.query(
                SELECT_EVENT_BY_PAYMENT_ID_SQL,
                (rs, rowNum) -> rs.getString("payload"),
                paymentId);
    }

    private Optional<PaymentEntity> retrievePaymentByIdempotencyKey(String idempotencyKey) {
        Optional<PaymentEntity> resultFromDb;
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

    private CreatePaymentRequest getValidPaymentRequest() {
        return CreatePaymentRequest.builder()
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .debtorRequest(DEBTOR_REQUEST)
                .creditorRequest(CREDITOR_REQUEST)
                .endToEndId(END_TO_END_ID)
                .build();
    }

}
