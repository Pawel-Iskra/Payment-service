package priv.home.paymentservice.outbox.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import priv.home.paymentservice.integration.AbstractIntegrationTest;
import priv.home.paymentservice.messaging.event.PaymentEventType;
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


    // for the tests correctness of payload is not necessary
    private static final String SAMPLE_PAYLOAD = "{\"eventId\":\"c32d0cae-a082-4633-95c8-add8fd4a8b41\"}";

    @Autowired
    private OutboxStorageJpa outboxStorageJpa;
    @Autowired
    private OutboxEventPublisherProperties publisherProperties;
    @Autowired
    private OutboxClaimService outboxClaimService;


    @BeforeEach
    void clearTable(){
        outboxStorageJpa.deleteAll();
    }

    @Test
    public void shouldReserveRecordsConcurrently() throws InterruptedException {
        // given
        OffsetDateTime startTime = OffsetDateTime.now().minusSeconds(1);
        int workers = 4;
        int timeoutSec = 5;
        int batchSize = 10;
        List<OutboxEventEntity> eventEntities = getOutboxEventEntityList(workers * batchSize);
        outboxStorageJpa.saveAllAndFlush(eventEntities);
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
            assertThat(batchEventsIds).hasSize(workers * batchSize);

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
    public void shouldProperlyCompleteClaim() {
        // given
        int batchSize = 5;
        Duration leaseDuration = publisherProperties.leaseDuration();
        UUID claimToken = UUID.randomUUID();
        List<OutboxEventEntity> eventEntitiesToBePublished = getOutboxEventEntityList(batchSize - 1);
        List<OutboxEventEntity> eventEntitiesToBeNotPublished = getOutboxEventEntityList(batchSize + 1);
        List<UUID> publishedEventsIds = eventEntitiesToBePublished.stream().map(OutboxEventEntity::getEventId).toList();
        List<UUID> notPublishedEventsIds = eventEntitiesToBeNotPublished.stream().map(OutboxEventEntity::getEventId).toList();
        List<PublishResult> publishResultsPublished = getPublishResultAsPublished(publishedEventsIds);
        List<PublishResult> publishResultsNotPublished = getPublishResultAsNotPublished(notPublishedEventsIds);
        List<OutboxEventEntity> allEntities = Stream.concat(
                eventEntitiesToBePublished.stream(),
                eventEntitiesToBeNotPublished.stream()).toList();
        allEntities.forEach(entity ->
                entity.reserve("worker", OffsetDateTime.now().plus(leaseDuration), claimToken));
        List<PublishResult> allPublishResults = Stream.concat(
                publishResultsPublished.stream(),
                publishResultsNotPublished.stream()).toList();
        outboxStorageJpa.saveAllAndFlush(allEntities);

        // when
        outboxClaimService.completeClaim(claimToken, allPublishResults);

        // then
        List<OutboxEventEntity> entitiesFromDb = outboxStorageJpa.findAll();
        entitiesFromDb.forEach(entity -> {
            assertThat(entity.getLockedBy()).isNull();
            assertThat(entity.getLockedUntil()).isNull();
            assertThat(entity.getClaimToken()).isNull();
            if (publishedEventsIds.contains(entity.getEventId())) {
                assertThat(entity.getFailedAt()).isNull();
                assertThat(entity.getPublishedAt()).isNotNull();
            } else {
                assertThat(entity.getPublishedAt()).isNull();
                assertThat(entity.getNextAttemptAt()).isNotNull();
                assertThat(entity.getLastError()).contains("Failed to publish the event");
            }
        });
    }


    private List<OutboxEventEntity> getOutboxEventEntityList(int amount) {
        return IntStream.range(0, amount)
                .mapToObj(n -> OutboxEventEntity.builder()
                        .eventId(UUID.randomUUID())
                        .paymentId(UUID.randomUUID())
                        .correlationId(UUID.randomUUID())
                        .createdAt(OffsetDateTime.now().minusMinutes(2))
                        .payload(SAMPLE_PAYLOAD)
                        .eventType(PaymentEventType.PAYMENT_CREATED_EVENT.name())
                        .build())
                .toList();
    }

    private List<PublishResult> getPublishResultAsPublished(List<UUID> eventIds) {
        return eventIds.stream()
                .map(PublishResult::buildPublished)
                .toList();
    }

    private List<PublishResult> getPublishResultAsNotPublished(List<UUID> eventIds) {
        return eventIds.stream()
                .map(evntId -> PublishResult.buildNotPublished(
                        evntId, new IllegalStateException("Failed to publish the event")))
                .toList();
    }

}