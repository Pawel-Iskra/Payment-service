package priv.home.paymentservice.data;

import priv.home.paymentservice.model.PaymentEntity;

import java.util.Optional;

public interface PaymentStorageJpaCustom {

    Optional<PaymentEntity> insertPaymentIfAbsent(PaymentEntity payment);
}
