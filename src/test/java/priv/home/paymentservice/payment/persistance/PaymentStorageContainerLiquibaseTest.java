package priv.home.paymentservice.payment.persistance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import priv.home.paymentservice.integration.AbstractIntegrationTest;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PaymentStorageContainerLiquibaseTest extends AbstractIntegrationTest {

    private static final String END_TO_END_ID = "E2E-20261108-001";
    private static final String DEBTOR_IBAN = "PL93109024023152899461927776";
    private static final String CREDITOR_IBAN = "PL82109024029148361863856384";
    private static final String IDEMPOTENCY_KEY_FIRST = "abc-123";
    private static final String IDEMPOTENCY_KEY_SECOND = "abc-124";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_PAYMENT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status, idempotency_key, end_to_end_id, debtor_iban, creditor_iban
            FROM payment
            WHERE  payment_id = ?""";
    private static final String INSERT_PAYMENT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status, idempotency_key, end_to_end_id, debtor_iban, creditor_iban)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PaymentStorageJpa paymentStorageJpa;

    @BeforeEach
    public void cleanTablePayment() {
        jdbcTemplate.update("DELETE FROM payment");
    }


    @Test
    public void shouldRetrievePaymentFromDb() {
        // given
        UUID paymentId = generateUuid();
        PaymentEntity payment = getValidPaymentEntity(IDEMPOTENCY_KEY_FIRST, paymentId);
        jdbcTemplate.update(
                INSERT_PAYMENT_SQL,
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentStatus().name(),
                payment.getIdempotencyKey(),
                payment.getEndToEndId(),
                payment.getDebtorIban(),
                payment.getCreditorIban());

        // when
        Optional<PaymentEntity> paymentFromDbOptional = paymentStorageJpa.findById(paymentId);

        // then
        assertTrue(paymentFromDbOptional.isPresent());
        PaymentEntity paymentFromDb = paymentFromDbOptional.get();
        assertEquals(paymentId, paymentFromDb.getPaymentId());
        assertEquals(VALID_AMOUNT, paymentFromDb.getAmount());
        assertEquals(CURRENCY_PLN, paymentFromDb.getCurrency());
        assertEquals(PaymentStatus.CREATED, paymentFromDb.getPaymentStatus());
        assertEquals(IDEMPOTENCY_KEY_FIRST, paymentFromDb.getIdempotencyKey());
        assertEquals(END_TO_END_ID, paymentFromDb.getEndToEndId());
        assertEquals(DEBTOR_IBAN, paymentFromDb.getDebtorIban());
        assertEquals(CREDITOR_IBAN, paymentFromDb.getCreditorIban());
    }

    @Test
    public void shouldAddPaymentToDb() {
        // given
        UUID paymentId = generateUuid();
        PaymentEntity payment = getValidPaymentEntity(IDEMPOTENCY_KEY_FIRST, paymentId);

        // when
        paymentStorageJpa.insertPaymentIfAbsent(payment);

        // then
        PaymentEntity paymentFromDb = jdbcTemplate.queryForObject(
                SELECT_PAYMENT_BY_ID_SQL,
                (resultSet, row) -> PaymentEntity.builder()
                        .paymentId(resultSet.getObject("payment_id", UUID.class))
                        .amount(resultSet.getBigDecimal("amount"))
                        .currency(resultSet.getString("currency"))
                        .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                        .idempotencyKey(resultSet.getString("idempotency_key"))
                        .endToEndId(resultSet.getString("end_to_end_id"))
                        .debtorIban(resultSet.getString("debtor_iban"))
                        .creditorIban(resultSet.getString("creditor_iban"))
                        .build(), paymentId);

        assertNotNull(paymentFromDb);
        assertEquals(paymentId, paymentFromDb.getPaymentId());
        assertEquals(VALID_AMOUNT, paymentFromDb.getAmount());
        assertEquals(CURRENCY_PLN, paymentFromDb.getCurrency());
        assertEquals(PaymentStatus.CREATED, paymentFromDb.getPaymentStatus());
        assertEquals(IDEMPOTENCY_KEY_FIRST, paymentFromDb.getIdempotencyKey());
        assertEquals(END_TO_END_ID, paymentFromDb.getEndToEndId());
        assertEquals(DEBTOR_IBAN, paymentFromDb.getDebtorIban());
        assertEquals(CREDITOR_IBAN, paymentFromDb.getCreditorIban());
    }

    @Test
    public void shouldReturnEmptyOptionalForNotExistingPayment() {
        // given
        UUID paymentId = generateUuid();

        // when
        Optional<PaymentEntity> paymentFromDbOptional = paymentStorageJpa.findById(paymentId);

        // then
        assertTrue(paymentFromDbOptional.isEmpty());
    }

    @Test
    public void testRaceConditionForInsertOfTheSamePaymentRequest() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        int threads = 2;
        int timeoutSec = 5;
        UUID paymentIdFirst = generateUuid();
        UUID paymentIdSecond = generateUuid();
        PaymentEntity paymentFirst = getValidPaymentEntity(IDEMPOTENCY_KEY_SECOND, paymentIdFirst);
        PaymentEntity paymentSecond = getValidPaymentEntity(IDEMPOTENCY_KEY_SECOND, paymentIdSecond);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Optional<PaymentEntity>> insertPaymentOne = () -> {
            readyLatch.countDown();
            startLatch.await();
            return paymentStorageJpa.insertPaymentIfAbsent(paymentFirst);
        };

        Callable<Optional<PaymentEntity>> insertPaymentTwo = () -> {
            readyLatch.countDown();
            startLatch.await();
            return paymentStorageJpa.insertPaymentIfAbsent(paymentSecond);
        };

        // when + then
        try {
            Future<Optional<PaymentEntity>> first = executor.submit(insertPaymentOne);
            Future<Optional<PaymentEntity>> second = executor.submit(insertPaymentTwo);
            assertThat(readyLatch.await(timeoutSec, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            Optional<PaymentEntity> firstThreadResult = first.get(timeoutSec, TimeUnit.SECONDS);
            Optional<PaymentEntity> secondThreadResult = second.get(timeoutSec, TimeUnit.SECONDS);
            assertThat(firstThreadResult.isPresent()).isNotEqualTo(secondThreadResult.isPresent());
        } finally {
            startLatch.countDown();
            executor.shutdownNow();
        }

        Integer countRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Integer.class);
        assertThat(countRecords).isOne();
    }


    private PaymentEntity getValidPaymentEntity(String idempotencyKey, UUID paymentId) {
        return PaymentEntity.builder()
                .paymentId(paymentId)
                .endToEndId(END_TO_END_ID)
                .debtorIban(DEBTOR_IBAN)
                .creditorIban(CREDITOR_IBAN)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}