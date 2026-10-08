package priv.home.paymentservice.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@Testcontainers
class PaymentStorageContainerLiquibaseTest {

    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final String POSTGRES_IMAGE = "postgres:18";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  payment_id = ?""";
    private static final String INSERT_PAYMENT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status)
            VALUES (?, ?, ?, ?)""";


    @Container
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_IMAGE);
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PaymentStorageJdbc paymentStorageJdbc;



    @Test
    public void shouldRetrievePaymentFromDb() {
        // given
        UUID paymentId = generateUuid();
        Payment payment = getValidPayment(paymentId);
        jdbcTemplate.update(
                INSERT_PAYMENT_SQL,
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentStatus().name());

        // when
        Optional<Payment> paymentFromDbOptional = paymentStorageJdbc.retrievePaymentByPaymentId(paymentId);

        // then
        Assertions.assertTrue(paymentFromDbOptional.isPresent());
        Payment paymentFromDb = paymentFromDbOptional.get();
        Assertions.assertEquals(paymentId, paymentFromDb.getPaymentId());
        Assertions.assertEquals(VALID_AMOUNT, paymentFromDb.getAmount());
        Assertions.assertEquals(CURRENCY_PLN, paymentFromDb.getCurrency());
        Assertions.assertEquals(PaymentStatus.CREATED, paymentFromDb.getPaymentStatus());
    }

    @Test
    public void shouldAddPaymentToDb() {
        // given
        UUID paymentId = generateUuid();
        Payment payment = getValidPayment(paymentId);

        // when
        paymentStorageJdbc.addPaymentToStorage(IDEMPOTENCY_KEY_VALUE, payment);

        // then
        Payment paymentFromDb = jdbcTemplate.queryForObject(
                SELECT_BY_ID_SQL,
                (resultSet, row) -> Payment.builder()
                        .paymentId(resultSet.getObject("payment_id", UUID.class))
                        .amount(resultSet.getBigDecimal("amount"))
                        .currency(resultSet.getString("currency"))
                        .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                        .build(), paymentId);

        Assertions.assertNotNull(paymentFromDb);
        Assertions.assertEquals(paymentId, paymentFromDb.getPaymentId());
        Assertions.assertEquals(VALID_AMOUNT, paymentFromDb.getAmount());
        Assertions.assertEquals(CURRENCY_PLN, paymentFromDb.getCurrency());
        Assertions.assertEquals(PaymentStatus.CREATED, paymentFromDb.getPaymentStatus());
    }

    @Test
    public void shouldReturnEmptyOptionalForNotExistingPayment() {
        // given
        UUID paymentId = generateUuid();

        // when
        Optional<Payment> paymentFromDbOptional = paymentStorageJdbc.retrievePaymentByPaymentId(paymentId);

        // then
        Assertions.assertTrue(paymentFromDbOptional.isEmpty());
    }


    private Payment getValidPayment(UUID paymentId) {
        return Payment.builder()
                .paymentId(paymentId)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}