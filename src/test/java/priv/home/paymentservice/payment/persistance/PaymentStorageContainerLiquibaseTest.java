package priv.home.paymentservice.payment.persistance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class PaymentStorageContainerLiquibaseTest {

    private static final String POSTGRES_IMAGE = "postgres:18";
    private static final String IDEMPOTENCY_KEY_FIRST = "abc-123";
    private static final String IDEMPOTENCY_KEY_SECOND = "abc-124";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_PAYMENT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status, idempotency_key
            FROM payment
            WHERE  payment_id = ?""";
    private static final String INSERT_PAYMENT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status, idempotency_key)
            VALUES (?, ?, ?, ?, ?)""";


    @Container
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }

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
                payment.getIdempotencyKey());

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
                        .build(), paymentId);

        assertNotNull(paymentFromDb);
        assertEquals(paymentId, paymentFromDb.getPaymentId());
        assertEquals(VALID_AMOUNT, paymentFromDb.getAmount());
        assertEquals(CURRENCY_PLN, paymentFromDb.getCurrency());
        assertEquals(PaymentStatus.CREATED, paymentFromDb.getPaymentStatus());
        assertEquals(IDEMPOTENCY_KEY_FIRST, paymentFromDb.getIdempotencyKey());
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
    public void testRaceConditionForInsertOfTheSamePaymentRequest() throws ExecutionException, InterruptedException {
        // given
        UUID paymentId = generateUuid();
        PaymentEntity payment = getValidPaymentEntity(IDEMPOTENCY_KEY_SECOND, paymentId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startThreadsForInsert = new CountDownLatch(1);

        Callable<Optional<PaymentEntity>> insertPayment = () -> {
            startThreadsForInsert.await();
            return paymentStorageJpa.insertPaymentIfAbsent(payment);
        };
        Future<Optional<PaymentEntity>> first = executor.submit(insertPayment);
        Future<Optional<PaymentEntity>> second = executor.submit(insertPayment);

        // when
        startThreadsForInsert.countDown();

        // then
        Optional<PaymentEntity> firstThreadResult = first.get();
        Optional<PaymentEntity> secondThreadResult = second.get();
        executor.shutdown();
        assertThat(firstThreadResult.isPresent()).isNotEqualTo(secondThreadResult.isPresent());

        Integer countRecords = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Integer.class);
        assertThat(countRecords).isOne();
    }


    private PaymentEntity getValidPaymentEntity(String idempotencyKey, UUID paymentId) {
        return PaymentEntity.builder()
                .paymentId(paymentId)
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