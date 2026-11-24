package priv.home.paymentservice.outbox.application;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.model.PublishResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventReleaserServiceConcurrent {

    private final PaymentEventPublisher paymentEventPublisher;
    private final OutboxEventPublisherProperties publisherProperties;
    private final OutboxClaimService outboxClaimService;
    private final ExecutorService executorService;
    private final OutboxRetryPolicy outboxRetryPolicy;


    public void publishPaymentCreatedEventsWithMultiWorkers() {
        int workers = publisherProperties.publishingWorkerCount();
        for (int i = 0; i < workers; i++) {
            String workerId = "outbox-worker-" + i;
            executorService.submit(() -> processEvents(workerId));
        }
    }


    private void processEvents(String workerId) {
        log.info("Outbox worker started : {}", workerId);
        int batchSize = publisherProperties.batchSize();
        Duration leaseDuration = publisherProperties.leaseDuration();

        ReservedOutboxBatch batch;
        do {
            batch = outboxClaimService.reserveEvents(workerId, batchSize, leaseDuration);
            if (!batch.eventsToPublish().isEmpty()) {
                List<PublishResult> publishResults = paymentEventPublisher.publishPaymentEvents(batch.eventsToPublish());
                outboxClaimService.completeClaim(batch.claimToken(), publishResults);
            }

        } while (!batch.eventsToPublish().isEmpty());

        log.info("Outbox worker finished: {}", workerId);
    }

}