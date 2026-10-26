package priv.home.paymentservice.payment.persistence;

import java.util.Optional;

public interface PaymentStorageJpaCustom {

    Optional<PaymentEntity> insertPaymentIfAbsent(PaymentEntity payment);
}
