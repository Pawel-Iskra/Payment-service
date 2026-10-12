package priv.home.paymentservice.eventpublisher;

import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;

import java.util.Collection;

public interface PaymentEventPublisher {

    void publishPaymentEvents(Collection<PaymentCreatedEvent> eventsToPublish);
}
