package priv.home.paymentservice.data;

import jakarta.persistence.*;
import lombok.*;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;


@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "idempotency_key", unique = true, nullable = false, updatable = false)
    private String idempotencyKey;

}
