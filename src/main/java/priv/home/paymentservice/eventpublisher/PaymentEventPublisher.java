package priv.home.paymentservice.eventpublisher;

import priv.home.paymentservice.model.Payment;

public interface PaymentEventPublisher {

    void publish(String idempotencyKey, Payment paymentToPublish);
}
