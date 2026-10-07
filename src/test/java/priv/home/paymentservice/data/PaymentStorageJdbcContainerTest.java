package priv.home.paymentservice.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.UUID;


@Testcontainers
class PaymentStorageJdbcContainerTest {

    private static final String POSTGRES_IMAGE = "postgres:18";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  payment_id = ?""";


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

        jdbcTemplate.execute("""
                CREATE TABLE payment (
                    payment_id UUID PRIMARY KEY,
                    amount DECIMAL(12, 2) NOT NULL,
                    currency VARCHAR(3) NOT NULL,
                    payment_status VARCHAR(256) NOT NULL
                )
                """);
    }


    @Test
    public void shouldAddPaymentToDb() {
        // given
        UUID paymentId = generateUuid();
        Payment payment = getValidPayment(paymentId);

        // when
        paymentStorageJdbc.addPaymentToStorage(payment);

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