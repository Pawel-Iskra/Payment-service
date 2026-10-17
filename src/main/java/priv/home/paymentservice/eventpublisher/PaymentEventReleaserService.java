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
        log.info("Amount of events to publish = {}", outboxEventsToPublish.size());

        List<UUID> listOfAcknowledgedEventsId = paymentEventPublisher.publishPaymentEvents(outboxEventsToPublish);
        log.info("Amount of published events acknowledged by publisher = {}", listOfAcknowledgedEventsId.size());
        int amountOfUpdatedRecords = outboxStorageJdbc.markEventsAsPublished(listOfAcknowledgedEventsId);
    }
}
