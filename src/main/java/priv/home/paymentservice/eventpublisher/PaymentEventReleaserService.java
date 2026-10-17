package priv.home.paymentservice.eventpublisher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.OutboxEventToPublish;
import priv.home.paymentservice.data.OutboxStorageJdbc;

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
        log.info("START of event publishing. Amount of events = {}", outboxEventsToPublish.size());

        List<UUID> listOfAcknowledgedEventsId = paymentEventPublisher.publishPaymentEvents(outboxEventsToPublish);
        log.info("END of event publishing. Amount of published events acknowledged by publisher = {}", listOfAcknowledgedEventsId.size());
        int updatedRecordsInOutboxEventTAble = outboxStorageJdbc.markEventsAsPublished(listOfAcknowledgedEventsId);
    }
}
