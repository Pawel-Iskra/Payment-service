package priv.home.paymentservice.eventpublisher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import priv.home.paymentservice.data.OutboxEventEntity;
import priv.home.paymentservice.data.OutboxStorageJpa;
import priv.home.paymentservice.eventpublisher.model.PublishResult;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentEventReleaserServiceTest {

    // for unit test correctness of payload is not necessary
    private static final String SAMPLE_PAYLOAD = "{\"eventId\":\"c32d0cae-a082-4633-95c8-add8fd4a8b41\"}";
    private static final String NO_UNPUBLISHED_EVENTS_FOUND = "No unpublished events found";
    private static final String AMOUNT_EVENTS_TO_PUBLISH = "Amount of events to publish = %s";
    private static final String AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED = "Amount of published events acknowledged by publisher = %s";
    private static final String PUBLISH_EXCEPTION_MESSAGE = "Publish exception message";
    private static final Integer BATCH_SIZE = 5;
    private static final OutboxEventPublisherProperties.Retry VALID_PUBLISHER_RETRY_PROPERTIES =
            new OutboxEventPublisherProperties.Retry(7, 2, Duration.ofMinutes(2), Duration.ofHours(1));


    @Mock
    private OutboxStorageJpa outboxStorageJpa;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private OutboxEventPublisherProperties publisherProperties;

    @InjectMocks
    private PaymentEventReleaserService releaserService;


    @Test
    public void shouldPublishAllEventsAndUpdateEntities(CapturedOutput output) {
        // given
        List<OutboxEventEntity> outboxEventEntitiesPack = getOutboxEventEntityList(2);
        List<UUID> eventIds = outboxEventEntitiesPack.stream().map(OutboxEventEntity::getEventId).toList();
        List<PublishResult> publishResultList = getPublishResultAsPublished(outboxEventEntitiesPack);
        when(publisherProperties.batchSize()).thenReturn(BATCH_SIZE);
        when(outboxStorageJpa
                .findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
                        any(), any(), any()))
                .thenReturn(getWindowReturn(outboxEventEntitiesPack, false));
        when(paymentEventPublisher.publishPaymentEvents(any()))
                .thenReturn(publishResultList);
        ArgumentCaptor<Collection<OutboxEventEntity>> captor = ArgumentCaptor.captor();

        // when
        releaserService.publishPaymentCreatedEvents();
        verify(outboxStorageJpa).saveAll(captor.capture());

        // then
        List<OutboxEventEntity> capturedEntities = captor.getValue().stream().toList();
        assertThat(capturedEntities).isNotEmpty();
        assertThat(capturedEntities.getFirst()).satisfies(outboxEntity -> {
            assertThat(eventIds).contains(outboxEntity.getEventId());
            assertThat(outboxEntity.getPublishedAt()).isNotNull();
            assertThat(outboxEntity.getNextAttemptAt()).isNotNull();
            assertThat(outboxEntity.getAttemptCount()).isOne();
            assertThat(outboxEntity.getFailedAt()).isNull();
            assertThat(outboxEntity.getLastError()).isNull();
        });
        assertThat(capturedEntities.getLast()).satisfies(outboxEntity -> {
            assertThat(eventIds).contains(outboxEntity.getEventId());
            assertThat(outboxEntity.getPublishedAt()).isNotNull();
            assertThat(outboxEntity.getNextAttemptAt()).isNotNull();
            assertThat(outboxEntity.getAttemptCount()).isOne();
            assertThat(outboxEntity.getFailedAt()).isNull();
            assertThat(outboxEntity.getLastError()).isNull();
        });
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(outboxEventEntitiesPack.size()));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(outboxEventEntitiesPack.size()));
    }


    @Test
    public void shouldUpdateEntitiesWhenPublishResultContainsNotPublishedAndPublished(CapturedOutput output) {
        // given
        String lastError = IllegalStateException.class.getName() + ", " + PUBLISH_EXCEPTION_MESSAGE;
        OutboxEventEntity outboxEntityFirst = getOutboxEventEntity(0);
        OutboxEventEntity outboxEntitySecond = getOutboxEventEntity(2, lastError, OffsetDateTime.now());
        OutboxEventEntity outboxEntityThird = getOutboxEventEntity(VALID_PUBLISHER_RETRY_PROPERTIES.maxAttempt() - 1);
        List<OutboxEventEntity> outboxEventEntitiesPack = List.of(outboxEntityFirst, outboxEntitySecond, outboxEntityThird);
        List<UUID> eventIds = outboxEventEntitiesPack.stream()
                .map(OutboxEventEntity::getEventId)
                .toList();
        List<PublishResult> publishResultNotPublishedList = getPublishResultAsNotPublished(List.of(outboxEntityFirst, outboxEntityThird));
        List<PublishResult> publishResultPublishedList = getPublishResultAsPublished(List.of(outboxEntitySecond));
        List<PublishResult> publishResultList = new ArrayList<>(publishResultPublishedList);
        publishResultList.addAll(publishResultNotPublishedList);
        when(publisherProperties.batchSize()).thenReturn(BATCH_SIZE);
        when(publisherProperties.retry()).thenReturn(VALID_PUBLISHER_RETRY_PROPERTIES);
        when(outboxStorageJpa
                .findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
                        any(), any(), any()))
                .thenReturn(getWindowReturn(outboxEventEntitiesPack, false));
        when(paymentEventPublisher.publishPaymentEvents(any()))
                .thenReturn(publishResultList);
        ArgumentCaptor<Collection<OutboxEventEntity>> captor = ArgumentCaptor.captor();
        OffsetDateTime timeBeforePublishStart = OffsetDateTime.now();

        // when
        releaserService.publishPaymentCreatedEvents();
        verify(outboxStorageJpa).saveAll(captor.capture());

        // then
        List<OutboxEventEntity> capturedEntities = captor.getValue().stream()
                .sorted(Comparator.comparing(OutboxEventEntity::getAttemptCount))
                .toList();
        // TODO: filter by eventId
        /*
        assertThat(capturedEntities)
        .filteredOn(entity -> entity.getEventId().equals(outboxEntityFirst.getEventId()))
        .singleElement()
        .satisfies(entity -> {
            assertThat(entity.getAttemptCount()).isEqualTo((short) 1);
            assertThat(entity.getPublishedAt()).isNull();
            assertThat(entity.getFailedAt()).isNull();
            assertThat(entity.getLastError()).contains(PUBLISH_EXCEPTION_MESSAGE);
        });
         */
        assertThat(capturedEntities).isNotEmpty();
        assertThat(capturedEntities.getFirst()).satisfies(outboxEntity -> {
            assertThat(eventIds).contains(outboxEntity.getEventId());
            assertThat(outboxEntity.getPublishedAt()).isNull();
            assertThat(outboxEntity.getNextAttemptAt()).isNotNull(); // TODO: check backoff calc
            assertThat(outboxEntity.getAttemptCount()).isOne();
            assertThat(outboxEntity.getLastError())
                    .isNotNull()
                    .contains(IllegalStateException.class.getName())
                    .contains(PUBLISH_EXCEPTION_MESSAGE);
            assertThat(outboxEntity.getFailedAt()).isNull();
        });
        assertThat(capturedEntities.get(1)).satisfies(outboxEntity -> {
            assertThat(eventIds).contains(outboxEntity.getEventId());
            assertThat(outboxEntity.getPublishedAt()).isNotNull();
            assertThat(outboxEntity.getNextAttemptAt()).isNotNull();
            assertThat(outboxEntity.getAttemptCount()).isEqualTo((short) 3);
            assertThat(outboxEntity.getLastError())
                    .isNotNull()
                    .contains(IllegalStateException.class.getName())
                    .contains(PUBLISH_EXCEPTION_MESSAGE);
            assertThat(outboxEntity.getFailedAt()).isNull();
        });
        assertThat(capturedEntities.getLast()).satisfies(outboxEntity -> {
            assertThat(eventIds).contains(outboxEntity.getEventId());
            assertThat(outboxEntity.getPublishedAt()).isNull();
            assertThat(outboxEntity.getNextAttemptAt()).isNotNull(); // TODO: check backoff calc
            assertThat(outboxEntity.getAttemptCount()).isEqualTo((short) VALID_PUBLISHER_RETRY_PROPERTIES.maxAttempt());
            assertThat(outboxEntity.getLastError())
                    .isNotNull()
                    .contains(IllegalStateException.class.getName())
                    .contains(PUBLISH_EXCEPTION_MESSAGE);
            assertThat(outboxEntity.getFailedAt()).isNotNull();
        });
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(outboxEventEntitiesPack.size()));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(1));
    }

    @Test
    public void shouldPublishAllEventsAndUpdateEntitiesWhenEventsMoreThanOnePackage(CapturedOutput output) {
        // given
        int amountOfAllEvents = 8; // less than 2*batchSize
        int eventsInSecondGroup = amountOfAllEvents - BATCH_SIZE;
        List<OutboxEventEntity> outboxEventEntitiesPackFirst = getOutboxEventEntityList(BATCH_SIZE);
        List<OutboxEventEntity> outboxEventEntitiesPackSecond = getOutboxEventEntityList(eventsInSecondGroup);
        List<PublishResult> publishResultListFirst = getPublishResultAsPublished(outboxEventEntitiesPackFirst);
        List<PublishResult> publishResultListSecond = getPublishResultAsPublished(outboxEventEntitiesPackSecond);
        when(publisherProperties.batchSize()).thenReturn(BATCH_SIZE);
        when(outboxStorageJpa
                .findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
                        any(), any(), any()))
                .thenReturn(getWindowReturn(outboxEventEntitiesPackFirst, true))
                .thenReturn(getWindowReturn(outboxEventEntitiesPackSecond, false));
        when(paymentEventPublisher.publishPaymentEvents(any()))
                .thenReturn(publishResultListFirst)
                .thenReturn(publishResultListSecond);
        ArgumentCaptor<Collection<OutboxEventEntity>> captor = ArgumentCaptor.captor();

        // when
        releaserService.publishPaymentCreatedEvents();
        verify(outboxStorageJpa, times(2)).saveAll(captor.capture());

        // then
        List<OutboxEventEntity> capturedEntitiesFirst = captor.getAllValues().stream().flatMap(Collection::stream).toList();
        assertThat(capturedEntitiesFirst).hasSize(8);

        assertThat(output.getOut()).contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(BATCH_SIZE));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(eventsInSecondGroup));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(BATCH_SIZE));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(eventsInSecondGroup));
    }


    @Test
    public void shouldNotPublishWhenNoEligibleEvents(CapturedOutput output) {
        // given
        when(publisherProperties.batchSize()).thenReturn(BATCH_SIZE);
        when(outboxStorageJpa
                .findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
                        any(), any(), any()))
                .thenReturn(getWindowReturn(Collections.emptyList(), false));

        // when
        releaserService.publishPaymentCreatedEvents();

        // then
        verify(outboxStorageJpa, times(0)).saveAll(any());
        verifyNoInteractions(paymentEventPublisher);
        assertThat(output.getOut()).contains(NO_UNPUBLISHED_EVENTS_FOUND);
    }


    private Duration calculateBackoff(int attemptsDone) {
        OutboxEventPublisherProperties.Retry retryProperties = VALID_PUBLISHER_RETRY_PROPERTIES;
        int multiplier = Math.powExact(retryProperties.multiplier(), attemptsDone);
        Duration calculatedBackoff = retryProperties.initialBackoff().multipliedBy(multiplier);
        return calculatedBackoff.compareTo(retryProperties.maxBackoff()) > 0 ?
                retryProperties.maxBackoff() : calculatedBackoff;
    }

    private List<OutboxEventEntity> getOutboxEventEntityList(int amount) {
        List<OutboxEventEntity> resultList = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            resultList.add(OutboxEventEntity.builder()
                    .eventId(generateUuid())
                    .paymentId(generateUuid())
                    .createdAt(OffsetDateTime.now().minusMinutes(5))
                    .payload(SAMPLE_PAYLOAD)
                    .attemptCount((short) 0)
                    .build());
        }
        return resultList;
    }

    private List<PublishResult> getPublishResultAsPublished(List<OutboxEventEntity> outboxEventEntitiesPack) {
        return outboxEventEntitiesPack.stream()
                .map(entity -> PublishResult.buildPublished(entity.getEventId()))
                .toList();
    }

    private List<PublishResult> getPublishResultAsNotPublished(List<OutboxEventEntity> outboxEventEntitiesPack) {
        return outboxEventEntitiesPack.stream()
                .map(entity -> PublishResult.buildNotPublished(
                        entity.getEventId(), new IllegalStateException(PUBLISH_EXCEPTION_MESSAGE)))
                .toList();
    }

    private Window<OutboxEventEntity> getWindowReturn(List<OutboxEventEntity> outboxEventEntitiesPack, boolean hasNext) {
        return Window.from(
                outboxEventEntitiesPack,
                index -> ScrollPosition.forward(Map.of(
                        "createdAt", outboxEventEntitiesPack.get(index).getCreatedAt(),
                        "eventId", outboxEventEntitiesPack.get(index).getEventId())),
                hasNext);
    }

    private OutboxEventEntity getOutboxEventEntity(int attemptCount) {
        return OutboxEventEntity.builder()
                .eventId(generateUuid())
                .paymentId(generateUuid())
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .payload(SAMPLE_PAYLOAD)
                .attemptCount((short) attemptCount)
                .build();
    }

    private OutboxEventEntity getOutboxEventEntity(int attemptCount, String lastError, OffsetDateTime nextAttemptAt) {
        return OutboxEventEntity.builder()
                .eventId(generateUuid())
                .paymentId(generateUuid())
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .payload(SAMPLE_PAYLOAD)
                .attemptCount((short) attemptCount)
                .lastError(lastError)
                .nextAttemptAt(nextAttemptAt)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}