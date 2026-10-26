package priv.home.paymentservice.payment.persistence;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
public class PaymentStorageJpaCustomImpl implements PaymentStorageJpaCustom {

    private static final String INSERT_PAYMENT_ON_CONFLICT_DO_NOTHING_SQL = """
            INSERT INTO payment (payment_id, amount, currency, payment_status, idempotency_key)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING payment_id, amount, currency, payment_status, idempotency_key""";
    private static final RowMapper<PaymentEntity> PAYMENT_ENTITY_ROW_MAPPER =
            (resultSet, rowNum) -> PaymentEntity.builder()
                    .paymentId(resultSet.getObject("payment_id", UUID.class))
                    .amount(resultSet.getBigDecimal("amount"))
                    .currency(resultSet.getString("currency"))
                    .paymentStatus(PaymentStatus.valueOf(resultSet.getString("payment_status")))
                    .idempotencyKey(resultSet.getString("idempotency_key"))
                    .build();


    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<PaymentEntity> insertPaymentIfAbsent(PaymentEntity payment) {
        return jdbcTemplate.query(
                INSERT_PAYMENT_ON_CONFLICT_DO_NOTHING_SQL,
                resultSet -> resultSet.next() ?
                        Optional.of(PAYMENT_ENTITY_ROW_MAPPER.mapRow(resultSet, 0)) : Optional.empty(),
                payment.getPaymentId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentStatus().name(),
                payment.getIdempotencyKey()
        );
    }
}
