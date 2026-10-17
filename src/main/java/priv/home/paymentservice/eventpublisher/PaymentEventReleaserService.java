package priv.home.paymentservice.eventpublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxEventToPublish;
import priv.home.paymentservice.data.OutboxStorageJdbc;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventReleaserService {

    private final OutboxStorageJdbc outboxStorageJdbc;
    private final PaymentEventPublisher paymentEventPublisher;


    public void publishPaymentCreatedEvents() {
        List<OutboxEventToPublish> outboxEventsToPublish = outboxStorageJdbc.retrieveEventsForPublish();
        log.info("Amount of events = {}, paymentIds= {}",
                outboxEventsToPublish.size(),
                outboxEventsToPublish.stream()
                        .map(OutboxEventToPublish::paymentCreatedEvent)
                        .map(PaymentCreatedEvent::getPaymentId)
                        .toList());
        List<UUID> listOfAcknowledgedSendEvents = paymentEventPublisher.publishPaymentEvents(outboxEventsToPublish);
        log.info("Send acknowledged from publisher = {}, eventIds= {}",
                listOfAcknowledgedSendEvents.size(),
                listOfAcknowledgedSendEvents);
        // TODO: mark as published in outbox_event
    }
}
