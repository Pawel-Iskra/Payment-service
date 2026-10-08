package priv.home.paymentservice.data;

import lombok.AllArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
public class PaymentStorageJdbc {


    private static final String INSERT_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status, idempotency_key)
            VALUES (?, ?, ?, ?, ?)""";
    private static final String SELECT_BY_ID_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  payment_id = ?""";
    private static final String SELECT_BY_IDEMPOTENCY_KEY_SQL = """
            SELECT payment_id, amount, currency, payment_status
            FROM payment
            WHERE  idempotency_key = ?""";

    private final JdbcTemplate jdbcTemplate;


    public void addPaymentToStorage(String idempotencyKey, Payment payment) {
        jdbcTemplate.update(
                INSERT_SQL,
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentStatus().name(),
                idempotencyKey
        );
    }

    public Optional<Payment> retrievePaymentByPaymentId(UUID paymentId) {
        Optional<Payment> resultFromDb;
        try {
            resultFromDb = Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_ID_SQL,
                    (resultSet, row) -> Payment.builder()
                            .paymentId(resultSet.getObject("payment_id", UUID.class))
                            .amount(resultSet.getBigDecimal("amount"))
                            .currency(resultSet.getString("currency"))
                            .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                            .build(), paymentId));
        } catch (EmptyResultDataAccessException exception) {
            resultFromDb = Optional.empty();
        }
        //  try/catch: infrastructure layer explains infra exception into the contract required by the higher layer
        return resultFromDb;
    }

    public Optional<Payment> retrievePaymentByIdempotencyKey(String idempotencyKey) {
        Optional<Payment> resultFromDb;
        try {
            resultFromDb = Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_IDEMPOTENCY_KEY_SQL,
                    (resultSet, row) -> Payment.builder()
                            .paymentId(resultSet.getObject("payment_id", UUID.class))
                            .amount(resultSet.getBigDecimal("amount"))
                            .currency(resultSet.getString("currency"))
                            .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                            .build(), idempotencyKey));
        } catch (EmptyResultDataAccessException exception) {
            resultFromDb = Optional.empty();
        }
        //  try/catch: infrastructure layer explains infra exception into the contract required by the higher layer
        return resultFromDb;
    }
}
