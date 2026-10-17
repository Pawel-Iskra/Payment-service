package priv.home.paymentservice.eventpublisher;

import priv.home.paymentservice.data.OutboxEventToPublish;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PaymentEventPublisher {

    List<UUID> publishPaymentEvents(Collection<OutboxEventToPublish> eventsToPublish);
}
