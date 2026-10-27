package priv.home.paymentservice.outbox.application;

import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.outbox.model.PublishResult;

import java.util.Collection;
import java.util.List;

public interface PaymentEventPublisher {

    List<PublishResult> publishPaymentEvents(Collection<OutboxEventToPublish> eventsToPublish);
}
