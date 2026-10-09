package priv.home.paymentservice.publisher;

import priv.home.paymentservice.model.Payment;

public interface PaymentEventPublisher {

    void publish(String idempotencyKey, Payment paymentToPublish);
}
