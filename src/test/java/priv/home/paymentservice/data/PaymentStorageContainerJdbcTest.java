package priv.home.paymentservice.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;


//@Testcontainers
// left as jdbc option
class PaymentStorageContainerJdbcTest {

    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final String POSTGRES_IMAGE = "postgres:18";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  payment_id = ?""";
    private static final String CREATE_TABLE_PAYMENT_SQL = """
            CREATE TABLE payment (
                payment_id UUID PRIMARY KEY,
                amount DECIMAL(12, 2) NOT NULL,
                currency VARCHAR(3) NOT NULL,
                payment_status VARCHAR(256) NOT NULL
            )
            """;
    private static final String INSERT_PAYMENT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status)
            VALUES (?, ?, ?, ?)""";


    @Container
    private final PostgreSQLContainer postgresContainer = new PostgreSQLContainer(POSTGRES_IMAGE);

    private JdbcTemplate jdbcTemplate;
    private PaymentStorageJdbc paymentStorageJdbc;


    @BeforeEach
    void setUp() {
        DataSource dataSource = DataSourceBuilder.create()
                .url(postgresContainer.getJdbcUrl())
                .username(postgresContainer.getUsername())
                .password(postgresContainer.getPassword())
                .build();

        jdbcTemplate = new JdbcTemplate(dataSource);
        paymentStorageJdbc = new PaymentStorageJdbc(jdbcTemplate);
        jdbcTemplate.execute(CREATE_TABLE_PAYMENT_SQL);
    }


//    @Test
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

//    @Test
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

//    @Test
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