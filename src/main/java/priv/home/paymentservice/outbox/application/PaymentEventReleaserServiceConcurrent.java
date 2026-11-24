package priv.home.paymentservice.outbox.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.outbox.model.ReservedOutboxBatch;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "payment-service.outbox.publisher",
        name = "publishing-mode",
        havingValue = "CONCURRENT"
)
public class PaymentEventReleaserServiceConcurrent {

    private final PaymentEventPublisher paymentEventPublisher;
    private final OutboxEventPublisherProperties publisherProperties;
    private final OutboxClaimService outboxClaimService;
    private final ExecutorService executorService;


    public void publishPaymentCreatedEventsWithMultiWorkers() {
        int workers = publisherProperties.publishingWorkerCount();
        for (int i = 0; i < workers; i++) {
            String workerId = "outbox-worker-" + i;
            executorService.submit(() -> processEvents(workerId));
        }
    }


    private void processEvents(String workerId) {
        log.info("Outbox worker started = {}", workerId);
        int batchSize = publisherProperties.batchSize();
        Duration leaseDuration = publisherProperties.leaseDuration();

        try {
            ReservedOutboxBatch batch;
            do {
                batch = outboxClaimService.reserveEvents(workerId, batchSize, leaseDuration);
                if (!batch.eventsToPublish().isEmpty()) {
                    List<PublishResult> publishResults = paymentEventPublisher.publishPaymentEvents(batch.eventsToPublish());
                    outboxClaimService.completeClaim(batch.claimToken(), publishResults);
                }
            } while (batch.eventsToPublish().size() == batchSize);
        } catch (Exception exception) {
            log.error("Outbox worker failed = {}", workerId, exception);
        } finally {
            log.info("Outbox worker finished = {}", workerId);
        }
    }

}