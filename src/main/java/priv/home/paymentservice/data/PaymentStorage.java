package priv.home.paymentservice.data;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
public class PaymentStorage {


    private static final String INSERT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status)
            VALUES (?, ?, ?, ?)""";

    private static final String SELECT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  payment_id = ?""";


    private final JdbcTemplate jdbcTemplate;


    public void addPaymentToStorage(Payment payment) {
        jdbcTemplate.update(
                INSERT_SQL,
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentStatus().name()
        );
    }

    public Optional<Payment> retrievePaymentByPaymentId(UUID paymentId) {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject(
                        SELECT_BY_ID_SQL,
                        (resultSet, row) -> Payment.builder()
                                .paymentId(resultSet.getObject("payment_id", UUID.class))
                                .amount(resultSet.getBigDecimal("amount"))
                                .currency(resultSet.getString("currency"))
                                .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                                .build(), paymentId));
    }
}
