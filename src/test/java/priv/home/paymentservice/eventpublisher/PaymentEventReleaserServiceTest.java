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
import java.util.stream.Collectors;

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
        int amountOfEventEntities = 3;
        OffsetDateTime timeBeforePublishStart = OffsetDateTime.now();
        List<OutboxEventEntity> outboxEventEntitiesPack = getOutboxEventEntityList(amountOfEventEntities);
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
        assertThat(capturedEntities).hasSize(amountOfEventEntities);
        OffsetDateTime timeAfterPublishEnd = OffsetDateTime.now().plusSeconds(1);
        assertThat(capturedEntities).allSatisfy(oneOfEvent -> {
            assertThat(eventIds).contains(oneOfEvent.getEventId());
            assertThat(oneOfEvent.getPublishedAt())
                    .isAfter(timeBeforePublishStart)
                    .isBefore(timeAfterPublishEnd);
            assertThat(oneOfEvent.getNextAttemptAt()).isNotNull();
            assertThat(oneOfEvent.getAttemptCount()).isOne();
            assertThat(oneOfEvent.getFailedAt()).isNull();
            assertThat(oneOfEvent.getLastError()).isNull();
        });
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(amountOfEventEntities));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(amountOfEventEntities));
    }

    @Test
    public void shouldUpdateEntitiesWhenPublishResultContainsNotPublishedAndPublished(CapturedOutput output) {
        // given
        int firstAttemptCount = 0;
        int secondAttemptCount = 2;
        int thirdAttemptCount = VALID_PUBLISHER_RETRY_PROPERTIES.maxAttempt() - 1;
        OffsetDateTime timeBeforePublishStart = OffsetDateTime.now();
        OffsetDateTime secondNextAttemptAt = OffsetDateTime.now().minusMinutes(1);
        String lastError = IllegalStateException.class.getName() + ", " + PUBLISH_EXCEPTION_MESSAGE;
        OutboxEventEntity outboxEntityFirst = getOutboxEventEntity(firstAttemptCount);
        OutboxEventEntity outboxEntitySecond = getOutboxEventEntity(secondAttemptCount, lastError, secondNextAttemptAt);
        OutboxEventEntity outboxEntityThird = getOutboxEventEntity(thirdAttemptCount, lastError, OffsetDateTime.now());
        List<OutboxEventEntity> outboxEventEntitiesPack = List.of(outboxEntityFirst, outboxEntitySecond, outboxEntityThird);
        List<PublishResult> publishResultPublishedList = getPublishResultAsPublished(List.of(outboxEntitySecond));
        List<PublishResult> publishResultNotPublishedList = getPublishResultAsNotPublished(List.of(outboxEntityFirst, outboxEntityThird));
        List<PublishResult> allPublishResultList = new ArrayList<>(publishResultPublishedList);
        allPublishResultList.addAll(publishResultNotPublishedList);
        when(publisherProperties.batchSize()).thenReturn(BATCH_SIZE);
        when(publisherProperties.retry()).thenReturn(VALID_PUBLISHER_RETRY_PROPERTIES);
        when(outboxStorageJpa
                .findByPublishedAtIsNullAndFailedAtIsNullAndNextAttemptAtIsLessThanEqualOrderByCreatedAtAscEventIdAsc(
                        any(), any(), any()))
                .thenReturn(getWindowReturn(outboxEventEntitiesPack, false));
        when(paymentEventPublisher.publishPaymentEvents(any()))
                .thenReturn(allPublishResultList);
        ArgumentCaptor<Collection<OutboxEventEntity>> captor = ArgumentCaptor.captor();

        // when
        releaserService.publishPaymentCreatedEvents();
        verify(outboxStorageJpa).saveAll(captor.capture());

        // then
        List<OutboxEventEntity> capturedEntities = captor.getValue().stream().toList();
        assertThat(capturedEntities).hasSize(outboxEventEntitiesPack.size());
        OffsetDateTime timeAfterPublishEnd = OffsetDateTime.now().plusSeconds(1);
        assertThat(capturedEntities)
                .filteredOn(entity -> entity.getEventId().equals(outboxEntityFirst.getEventId()))
                .singleElement()
                .satisfies(eventEntity -> {
                    assertThat(eventEntity.getPublishedAt()).isNull();
                    assertThat(eventEntity.getNextAttemptAt())
                            .isAfter(timeBeforePublishStart.plus(calculateBackoff(firstAttemptCount)))
                            .isBefore(timeAfterPublishEnd.plus(calculateBackoff(firstAttemptCount)));
                    assertThat(eventEntity.getAttemptCount()).isEqualTo((short) (firstAttemptCount + 1));
                    assertThat(eventEntity.getLastError())
                            .isNotNull()
                            .contains(IllegalStateException.class.getName())
                            .contains(PUBLISH_EXCEPTION_MESSAGE);
                    assertThat(eventEntity.getFailedAt()).isNull();
                });
        assertThat(capturedEntities)
                .filteredOn(eventEntity -> eventEntity.getEventId().equals(outboxEntitySecond.getEventId()))
                .singleElement()
                .satisfies(eventEntity -> {
                    assertThat(eventEntity.getPublishedAt())
                            .isAfter(timeBeforePublishStart)
                            .isBefore(timeAfterPublishEnd);
                    assertThat(eventEntity.getNextAttemptAt()).isEqualTo(secondNextAttemptAt);
                    assertThat(eventEntity.getAttemptCount()).isEqualTo((short) (secondAttemptCount + 1));
                    assertThat(eventEntity.getLastError())
                            .isNotNull()
                            .contains(IllegalStateException.class.getName())
                            .contains(PUBLISH_EXCEPTION_MESSAGE);
                    assertThat(eventEntity.getFailedAt()).isNull();
                });
        assertThat(capturedEntities)
                .filteredOn(entity -> entity.getEventId().equals(outboxEntityThird.getEventId()))
                .singleElement()
                .satisfies(outboxEntity -> {
                    assertThat(outboxEntity.getPublishedAt()).isNull();
                    assertThat(outboxEntity.getNextAttemptAt())
                            .isAfter(timeBeforePublishStart.plus(calculateBackoff(thirdAttemptCount)))
                            .isBefore(timeAfterPublishEnd.plus(calculateBackoff(thirdAttemptCount)));
                    assertThat(outboxEntity.getAttemptCount()).isEqualTo((short) VALID_PUBLISHER_RETRY_PROPERTIES.maxAttempt());
                    assertThat(outboxEntity.getLastError())
                            .isNotNull()
                            .contains(IllegalStateException.class.getName())
                            .contains(PUBLISH_EXCEPTION_MESSAGE);
                    assertThat(outboxEntity.getFailedAt())
                            .isAfter(timeBeforePublishStart)
                            .isBefore(timeAfterPublishEnd);
                });
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_TO_PUBLISH.formatted(outboxEventEntitiesPack.size()));
        assertThat(output.getOut()).contains(AMOUNT_EVENTS_PUBLISHED_AND_ACKNOWLEDGED.formatted(1));
    }

    @Test
    public void shouldPublishAllEventsAndUpdateEntitiesWhenMoreThanOnePackageOfEvents(CapturedOutput output) {
        // given
        int amountOfAllEvents = 2 * BATCH_SIZE - 1;
        int eventsInSecondGroup = amountOfAllEvents - BATCH_SIZE;
        List<OutboxEventEntity> outboxEventEntitiesPackFirst = getOutboxEventEntityList(BATCH_SIZE);
        List<OutboxEventEntity> outboxEventEntitiesPackSecond = getOutboxEventEntityList(eventsInSecondGroup);
        List<UUID> eventIds = outboxEventEntitiesPackFirst.stream().map(OutboxEventEntity::getEventId).collect(Collectors.toList());
        eventIds.addAll(outboxEventEntitiesPackSecond.stream().map(OutboxEventEntity::getEventId).toList());
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
        verify(outboxStorageJpa, times((int) Math.ceil((double) amountOfAllEvents / BATCH_SIZE))).saveAll(captor.capture());

        // then
        List<OutboxEventEntity> capturedEntities = captor.getAllValues().stream().flatMap(Collection::stream).toList();
        assertThat(capturedEntities).hasSize(amountOfAllEvents);
        assertThat(capturedEntities).allSatisfy(oneOfEvent -> {
            assertThat(eventIds).contains(oneOfEvent.getEventId());
            assertThat(oneOfEvent.getPublishedAt()).isNotNull();
            assertThat(oneOfEvent.getNextAttemptAt()).isNotNull();
            assertThat(oneOfEvent.getAttemptCount()).isOne();
            assertThat(oneOfEvent.getFailedAt()).isNull();
            assertThat(oneOfEvent.getLastError()).isNull();
        });
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