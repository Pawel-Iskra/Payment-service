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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final AtomicBoolean workersWorking = new AtomicBoolean(false);


    public void publishPaymentCreatedEventsWithMultiWorkers() {
        if (!workersWorking.compareAndSet(false, true)) {
            log.debug("Previous outbox publishing cycle is still running. Skipping this cycle.");
            return;
        }
        int workers = publisherProperties.publishingWorkerCount();
        AtomicInteger remainingWorkers = new AtomicInteger(workers);

        for (int i = 0; i < workers; i++) {
            String workerId = "outbox-worker-" + i;
            executorService.execute(() -> {
                try {
                    processEvents(workerId);
                } finally {
                    if (remainingWorkers.decrementAndGet() == 0) {
                        workersWorking.set(false);
                    }
                }
            });
        }
    }


    private void processEvents(String workerId) {
        log.info("Outbox worker started = {}", workerId);
        int publishedCount = 0;
        int failedCount = 0;

        int batchSize = publisherProperties.batchSize();
        Duration leaseDuration = publisherProperties.leaseDuration();

        try {
            ReservedOutboxBatch batch;
            do {
                batch = outboxClaimService.reserveEvents(workerId, batchSize, leaseDuration);
                if (!batch.eventsToPublish().isEmpty()) {
                    List<PublishResult> publishResults = paymentEventPublisher.publishPaymentEvents(batch.eventsToPublish());
                    outboxClaimService.completeClaim(batch.claimToken(), publishResults);
                    int batchPublishCount = (int) publishResults.stream()
                            .filter(PublishResult::isPublished)
                            .count();
                    publishedCount += batchPublishCount;
                    failedCount += publishResults.size() - batchPublishCount;
                }
            } while (batch.eventsToPublish().size() == batchSize);
        } catch (Exception exception) {
            log.error("Outbox worker failed = {}", workerId, exception);
        } finally {
            log.info("Outbox worker finished: workerId={}, processed={}, published={}, failed={}",
                    workerId, (publishedCount + failedCount), publishedCount, failedCount);
        }
    }

}