package priv.home.paymentservice.data;

import java.util.Optional;

public interface PaymentStorageJpaCustom {

    Optional<PaymentEntity> insertPaymentIfAbsent(PaymentEntity payment);
}
