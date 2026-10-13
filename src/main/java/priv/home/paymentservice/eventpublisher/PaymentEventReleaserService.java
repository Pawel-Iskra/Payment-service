package priv.home.paymentservice.eventpublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxStorageJdbc;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventReleaserService {

    private final OutboxStorageJdbc outboxStorageJdbc;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;


    public void publishPaymentCreatedEvents() {
        List<String> payloadsFromDbToPublish = outboxStorageJdbc.retrieveEventsForPublish();
        List<PaymentCreatedEvent> paymentCreatedEventsToPublish =
                payloadsFromDbToPublish.stream()
                        .map(payload -> objectMapper.readValue(payload, PaymentCreatedEvent.class))
                        .toList();
        log.info("Amount of events = {}, paymentIds= {}",
                paymentCreatedEventsToPublish.size(),
                paymentCreatedEventsToPublish.stream().map(PaymentCreatedEvent::getPaymentId).toList());
        paymentEventPublisher.publishPaymentEvents(paymentCreatedEventsToPublish);
        // TODO: update published_at in outbox_event --> nope
    }
}
