package priv.home.paymentservice.eventpublisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventReleaserService {

    private final PaymentStorageJdbc paymentStorageJdbc;
    private final PaymentEventPublisher paymentEventPublisher;


    public void publishPaymentCreatedEvents() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> payloadsFromDbToPublish = paymentStorageJdbc.retrieveEventsForPublish();
        List<PaymentCreatedEvent> paymentCreatedEventsToPublish =
                payloadsFromDbToPublish.stream()
                        .map(payload -> {
                            try {
                                return objectMapper.readValue(payload, PaymentCreatedEvent.class);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);  // todo: add desc
                            }
                        }).toList();
        log.info("Amount of events = {}, paymentIds= {}",
                paymentCreatedEventsToPublish.size(),
                paymentCreatedEventsToPublish.stream().map(PaymentCreatedEvent::getPaymentId).toList());
        paymentEventPublisher.publishPaymentEvents(paymentCreatedEventsToPublish);
        // TODO: update published_at in outbox_event
    }
}
