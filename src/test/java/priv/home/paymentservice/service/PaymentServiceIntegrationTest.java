package priv.home.paymentservice.service;

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
import priv.home.paymentservice.data.OutboxStorageJdbc;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
public class PaymentServiceIntegrationTest {

    private static final String POSTGRES_IMAGE = "postgres:18";
    private static final String IDEMPOTENCY_KEY_FIRST = "abc-123";
    private static final String IDEMPOTENCY_KEY_SECOND = "abc-124";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String SELECT_PAYMENT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  payment_id = ?""";
    private static final String INSERT_PAYMENT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status)
            VALUES (?, ?, ?, ?)""";


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
    private PaymentStorageJdbc paymentStorageJdbc;
    @MockitoSpyBean
    private OutboxStorageJdbc outboxStorageJdbc;
    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    public void cleanTables() {
        jdbcTemplate.update("DELETE FROM payment");
        jdbcTemplate.update("DELETE FROM outbox_event");
    }


    @Test
    public void shouldRollbackPaymentInsertIfOutboxInsertThrowsException() {
        // given
        UUID eventId = generateUuid();
        UUID paymentId = generateUuid();
        PaymentRequest paymentRequest = getValidPaymentRequest();
        Payment payment = getValidPaymentFromRequest(paymentId, paymentRequest);
        doThrow(new RuntimeException("Simulated outbox failure")).when(outboxStorageJdbc)
                .addPaymentEventToOutbox(any(UUID.class), any(UUID.class), any(String.class), any(String.class));

        // when
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                paymentService.createPayment(IDEMPOTENCY_KEY_FIRST, paymentRequest));

        // then
        assertThat(thrown.getMessage()).contains("Simulated outbox failure");
        // tODO: check paymentStorage

    }

    private Payment getValidPayment(UUID paymentId) {
        return Payment.builder()
                .paymentId(paymentId)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private PaymentRequest getValidPaymentRequest() {
        return new PaymentRequest(VALID_AMOUNT, CURRENCY_PLN);
    }

    private Payment getValidPaymentFromRequest(UUID paymentId, PaymentRequest validPaymentRequest) {
        return Payment.builder()
                .paymentId(paymentId)
                .amount(validPaymentRequest.amount())
                .currency(validPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}
