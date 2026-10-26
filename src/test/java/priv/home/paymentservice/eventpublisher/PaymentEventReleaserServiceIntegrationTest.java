package priv.home.paymentservice.eventpublisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.data.OutboxEventEntity;
import priv.home.paymentservice.data.OutboxStorageJpa;
import priv.home.paymentservice.eventpublisher.model.PublishResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "payment-service.outbox.publisher.batch-size=3")
@Testcontainers
@ExtendWith({OutputCaptureExtension.class})
class PaymentEventReleaserServiceIntegrationTest {

    // batch-size set to 3 in @SpringBootTest.properties
    private static final int BATCH_SIZE = 3;
    private static final String POSTGRES_DOCKER_EMAGE = "postgres:18";
    private static final String NO_UNPUBLISHED_EVENTS_FOUND = "No unpublished events found";
    private static final String AMOUNT_EVENTS_TO_PUBLISH = "Amount of events to publish = %s";
    private static final String AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED = "Amount of published events acknowledged by publisher = %s";
    private static final List<String> EVENTS_VALID_FOR_PUBLISH_IN_DB_EVENT_IDS = List.of(
            "86f1a6e1-85f7-444b-a18f-720c580beda4", "6562d66c-6a09-434f-b252-12942d714092",
            "9701c56b-ddaa-4ff6-82c3-18f1b73059be", "d8254ede-f250-4971-b929-339319e8a3e9",
            "a7eb2b0c-4e7b-4217-a31c-24b0df5becca", "0b4822fe-3c3d-4417-8f49-c5d92a483c0e",
            "c81fbb95-92a7-407d-9044-44c845bd7f40");
    private static final short[] EVENTS_VALID_FOR_PUBLISH_IN_DB_ATTEMPTS_COUNT = new short[]{0, 1, 3, 1, 0, 4, 0};

    @Autowired
    private OutboxStorageJpa outboxStorageJpa;
    @MockitoBean
    private PaymentEventPublisher paymentEventPublisher;

    @Autowired
    private PaymentEventReleaserService releaserService;

    @Container
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_DOCKER_EMAGE);

    @DynamicPropertySource
    static void configurePostgresContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }


    @Test
    @Sql(scripts = "/eventpublisher/insert_outbox_event_entities.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    public void shouldPublishEventsAndUpdateEntities(CapturedOutput capturedOutput) {
        // given -> in db is inserted 7 records with valid values for publishing
        OffsetDateTime timeBeforePublishStart = OffsetDateTime.now();
        List<UUID> eventIdFirstPack = EVENTS_VALID_FOR_PUBLISH_IN_DB_EVENT_IDS.stream()
                .limit(BATCH_SIZE)
                .map(UUID::fromString)
                .toList();
        List<UUID> eventIdSecondPack = EVENTS_VALID_FOR_PUBLISH_IN_DB_EVENT_IDS.stream()
                .skip(BATCH_SIZE)
                .limit(BATCH_SIZE)
                .map(UUID::fromString)
                .toList();
        List<UUID> eventIdThirdPack = EVENTS_VALID_FOR_PUBLISH_IN_DB_EVENT_IDS.stream()
                .skip(2 * BATCH_SIZE)
                .map(UUID::fromString)
                .toList();
        List<PublishResult> publishResultsFirstPack = getPublishResultAsPublished(eventIdFirstPack);
        List<PublishResult> publishResultsSecondPack = getPublishResultAsPublished(eventIdSecondPack);
        List<PublishResult> publishResultsThirdPack = getPublishResultAsPublished(eventIdThirdPack);

        when(paymentEventPublisher.publishPaymentEvents(any()))
                .thenReturn(publishResultsFirstPack)
                .thenReturn(publishResultsSecondPack)
                .thenReturn(publishResultsThirdPack);

        // when
        releaserService.publishPaymentCreatedEvents();

        // then
        assertThat(capturedOutput.getOut())
                .contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(BATCH_SIZE))
                .contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(BATCH_SIZE))
                .contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(1))
                .contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(1));

        OffsetDateTime timeAfterPublishEnd = OffsetDateTime.now();
        List<OutboxEventEntity> eventEntitiesFromDb = outboxStorageJpa.findAll();
        assertThat(eventEntitiesFromDb)
                .hasSize(EVENTS_VALID_FOR_PUBLISH_IN_DB_EVENT_IDS.size())
                .allSatisfy(currentEventEntity -> {
                    assertThat(currentEventEntity.getPublishedAt())
                            .isAfter(timeBeforePublishStart)
                            .isBefore(timeAfterPublishEnd);
                    assertThat(currentEventEntity.getFailedAt()).isNull();
                    assertThat(checkAttemptCount(currentEventEntity)).isTrue();
                });
    }


    private boolean checkAttemptCount(OutboxEventEntity currentEventEntity) {
        int index = EVENTS_VALID_FOR_PUBLISH_IN_DB_EVENT_IDS.indexOf(currentEventEntity.getEventId().toString());
        return (EVENTS_VALID_FOR_PUBLISH_IN_DB_ATTEMPTS_COUNT[index] + 1) == (int) currentEventEntity.getAttemptCount();
    }

    private List<PublishResult> getPublishResultAsPublished(List<UUID> eventIds) {
        return eventIds.stream()
                .map(PublishResult::buildPublished)
                .toList();
    }
}