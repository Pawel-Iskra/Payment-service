package priv.home.paymentservice.outbox.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import priv.home.paymentservice.integration.AbstractIntegrationTest;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.outbox.model.ReservedOutboxBatch;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxClaimServiceContainerTest extends AbstractIntegrationTest {

    @Autowired
    private OutboxStorageJpa outboxStorageJpa;
    @Autowired
    private OutboxEventPublisherProperties publisherProperties;
    @Autowired
    private OutboxClaimService outboxClaimService;


    @Test
    @Sql(scripts = "/eventpublisher/insert_40_outbox_events_eligible_for_publish.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    public void shouldReserveRecordsConcurrently() throws InterruptedException {
        // given inserted 40 outbox events eligible for publish
        OffsetDateTime startTime = OffsetDateTime.now().minusSeconds(1);
        int timeoutSec = 5;
        int workers = 4;
        int batchSize = 10;
        int insertedRecords = 40;
        ExecutorService executorService = Executors.newFixedThreadPool(workers);
        CountDownLatch readyLatch = new CountDownLatch(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        Duration leaseDuration = publisherProperties.leaseDuration();

        // when + then
        try {
            List<Future<ReservedOutboxBatch>> workersFutures = IntStream.range(0, workers)
                    .mapToObj(workerNumber ->
                            executorService.submit(() -> {
                                readyLatch.countDown();
                                startLatch.await();
                                return outboxClaimService.reserveEvents("worker-" + workerNumber, batchSize, leaseDuration);
                            })).toList();
            assertThat(readyLatch.await(timeoutSec, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<ReservedOutboxBatch> workersBatches = workersFutures.stream()
                    .map(future -> {
                        try {
                            return future.get(timeoutSec, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } catch (ExecutionException | TimeoutException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList();
            assertThat(workersBatches).hasSize(workers);

            Set<UUID> batchEventsIds = workersBatches.stream()
                    .flatMap(batch -> batch.eventsToPublish().stream())
                    .map(OutboxEventToPublish::eventId)
                    .collect(Collectors.toSet());
            assertThat(batchEventsIds).hasSize(insertedRecords);

            Set<UUID> claimTokens = workersBatches.stream()
                    .map(ReservedOutboxBatch::claimToken)
                    .collect(Collectors.toSet());
            assertThat(claimTokens).hasSize(workers);

            List<OutboxEventEntity> outboxEventEntitiesFromBd = outboxStorageJpa.findAll();
            OffsetDateTime endTime = OffsetDateTime.now().plusSeconds(1);
            outboxEventEntitiesFromBd.forEach(
                    reservedEvent -> assertThat(reservedEvent.getLockedUntil())
                            .isAfter(startTime.plus(leaseDuration))
                            .isBefore(endTime.plus(leaseDuration))
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @Sql(scripts = "/eventpublisher/insert_10_events_eligible_for_publish.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    public void shouldProperlyCompleteClaim() {
        // given inserted 10 records reserved by worker
        UUID claimTokenFromDb = UUID.fromString("d6d1d13a-2f4d-45f3-8bd1-a7f5d7658970");
        List<String> eventsIdsFromDbToBePublished = List.of("86f1a6e1-85f7-444b-a18f-720c580beda4", "6562d66c-6a09-434f-b252-12942d714092",
                "9701c56b-ddaa-4ff6-82c3-18f1b73059be", "d8254ede-f250-4971-b929-339319e8a3e9", "a7eb2b0c-4e7b-4217-a31c-24b0df5becca");
        List<String> eventsIdsFromDbToBeNotPublished = List.of("0b4822fe-3c3d-4417-8f49-c5d92a483c0e", "c81fbb95-92a7-407d-9044-44c845bd7f40",
                "86f1a6e1-85f7-444b-a18f-720c580beda1", "86f1a6e1-85f7-444b-a18f-720c580beda2", "86f1a6e1-85f7-444b-a18f-720c580beda3");
        List<PublishResult> publishResultsPublished = getPublishResultAsPublished(eventsIdsFromDbToBePublished);
        List<PublishResult> publishResultsNotPublished = getPublishResultAsNotPublished(eventsIdsFromDbToBeNotPublished);
        List<PublishResult> allPublishResults = Stream.concat(
                publishResultsPublished.stream(), publishResultsNotPublished.stream()
        ).toList();

        // when
        outboxClaimService.completeClaim(claimTokenFromDb, allPublishResults);

        // then
        List<OutboxEventEntity> entitiesFromDb = outboxStorageJpa.findAll();
        entitiesFromDb.forEach(entity -> {
            assertThat(entity.getLockedBy()).isNull();
            assertThat(entity.getLockedUntil()).isNull();
            assertThat(entity.getClaimToken()).isNull();
            assertThat(entity.getAttemptCount()).isOne();
            if (eventsIdsFromDbToBePublished.contains(entity.getEventId().toString())) {
                assertThat(entity.getFailedAt()).isNull();
                assertThat(entity.getPublishedAt()).isNotNull();
                assertThat(entity.getLastError()).isNull();
            } else {
                assertThat(entity.getPublishedAt()).isNull();
                assertThat(entity.getLastError()).contains("Failed to publish the event");
            }
        });
    }


    private List<PublishResult> getPublishResultAsPublished(List<String> eventIds) {
        return eventIds.stream()
                .map(UUID::fromString)
                .map(PublishResult::buildPublished)
                .toList();
    }

    private List<PublishResult> getPublishResultAsNotPublished(List<String> eventIds) {
        return eventIds.stream()
                .map(UUID::fromString)
                .map(evntId ->
                        PublishResult.buildNotPublished(evntId, new IllegalStateException("Failed to publish the event")))
                .toList();
    }
}