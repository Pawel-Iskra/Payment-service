package priv.home.paymentservice.outbox.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import priv.home.paymentservice.integration.IntegrationTestPostgresContainerConfig;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ActiveProfiles("local")
@SpringBootTest
@Import(IntegrationTestPostgresContainerConfig.class)
@ExtendWith({OutputCaptureExtension.class})
class PaymentEventReleaserServiceConcurrentIntegrationTest {


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> false);
        registry.add("payment-service.outbox.publisher.batch-size", () -> 10);
        registry.add("payment-service.outbox.publisher.retry.max-attempt", () -> 7);
        registry.add("payment-service.outbox.publisher.publishing-mode", () -> "CONCURRENT");
        registry.add("payment-service.outbox.publisher.publishing-worker-count", () -> 4);
    }

    @MockitoBean
    private PaymentEventPublisher paymentEventPublisher;
    @Autowired
    private OutboxStorageJpa outboxStorageJpa;
    @Autowired
    private PaymentEventReleaserServiceConcurrent paymentEventReleaserServiceConcurrent;


    @Test
    @Sql(scripts = "/eventpublisher/insert_outbox_events_eligible_for_publish.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    public void shouldReserveRecordsConcurrently(CapturedOutput capturedOutput) {
        // given inserted 100 records eligible for publishing
        int load = 100;
        List<OutboxEventEntity> eventEntities = outboxStorageJpa.findAll();
        List<UUID> eventsIds = eventEntities.stream()
                .map(OutboxEventEntity::getEventId)
                .toList();
        Queue<UUID> publishedEventsIdsQueue = new ConcurrentLinkedQueue<>();

        when(paymentEventPublisher.publishPaymentEvents(any()))
                .thenAnswer(invocation -> {
                    List<OutboxEventToPublish> publishEvents = invocation.getArgument(0);
                    return publishEvents.stream()
                            .map(event -> {
                                publishedEventsIdsQueue.add(event.eventId());
                                return PublishResult.buildPublished(event.eventId());
                            }).toList();
                });

        // when
        OffsetDateTime startTime = OffsetDateTime.now();
        paymentEventReleaserServiceConcurrent.publishPaymentCreatedEventsWithMultiWorkers();

        // then
        await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    List<OutboxEventEntity> entitiesFromDb = outboxStorageJpa.findAll();
                    assertThat(entitiesFromDb).hasSize(load);
                    assertThat(entitiesFromDb)
                            .allSatisfy(entity -> {
                                assertThat(entity.getLockedBy()).isNull();
                                assertThat(entity.getLockedUntil()).isNull();
                                assertThat(entity.getClaimToken()).isNull();
                            });
                });
        OffsetDateTime endTime = OffsetDateTime.now();
        assertThat(publishedEventsIdsQueue).hasSize(load);
        assertThat(publishedEventsIdsQueue).doesNotHaveDuplicates();
        assertThat(publishedEventsIdsQueue).containsExactlyInAnyOrderElementsOf(eventsIds);
        assertThat(Duration.between(endTime, startTime)).isLessThan(Duration.ofSeconds(10));
    }

}