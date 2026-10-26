package priv.home.paymentservice.eventpublisher;

import priv.home.paymentservice.eventpublisher.model.OutboxEventToPublish;
import priv.home.paymentservice.eventpublisher.model.PublishResult;

import java.util.Collection;
import java.util.List;

public interface PaymentEventPublisher {

    List<PublishResult> publishPaymentEvents(Collection<OutboxEventToPublish> eventsToPublish);
}
