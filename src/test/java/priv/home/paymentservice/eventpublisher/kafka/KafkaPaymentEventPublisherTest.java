package priv.home.paymentservice.eventpublisher.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.outbox.model.PublishResult;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.outbox.kafka.KafkaPaymentEventPublisher;
import priv.home.paymentservice.payment.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaPaymentEventPublisherTest {

    private static final String IDEMPOTENCY_KEY_FIRST = "abc-123";
    private static final String IDEMPOTENCY_KEY_SECOND = "abc-124";
    private static final String IDEMPOTENCY_KEY_THIRD = "abc-125";
    private static final String[] IDEMPOTENCY_KEYS = new String[]{
            IDEMPOTENCY_KEY_FIRST, IDEMPOTENCY_KEY_SECOND, IDEMPOTENCY_KEY_THIRD};
    private static final BigDecimal VALID_AMOUNT_FIRST = new BigDecimal("123.45");
    private static final BigDecimal VALID_AMOUNT_SECOND = new BigDecimal("223.45");
    private static final BigDecimal VALID_AMOUNT_THIRD = new BigDecimal("323.45");
    private static final BigDecimal[] VALID_AMOUNTS = new BigDecimal[]{
            VALID_AMOUNT_FIRST, VALID_AMOUNT_SECOND, VALID_AMOUNT_THIRD};
    private static final String CURRENCY_PLN = "PLN";
    private static final String KAFKA_TOPIC = "payment-created";
    private static final String EVENT_PUBLISHED_SUCCESSFULLY = "Event acknowledged by Kafka, eventId=";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaPaymentEventPublisher underTest;


    @BeforeEach
    public void setUp() {
        underTest = new KafkaPaymentEventPublisher(KAFKA_TOPIC, kafkaTemplate);
    }


    @Test
    public void shouldReturnAllPublishedResultsAsPublished(CapturedOutput output) {
        // given
        String eventType = PaymentEventType.PAYMENT_CREATED_EVENT.name();
        List<UUID> paymentIds = List.of(generateUuid(), generateUuid());
        List<UUID> eventIds = List.of(generateUuid(), generateUuid());
        List<PaymentCreatedEvent> paymentCreatedEvents = getListOfPaymentCreatedEvents(paymentIds, eventIds);
        List<String> payloads = getListOfPayloads(paymentCreatedEvents);
        List<OutboxEventToPublish> eventsToPublish = getListOfOutboxEventToPublish(eventIds, paymentIds, eventType, payloads);

        SendResult<String, String> sendResultFirst = new SendResult<>(new ProducerRecord<>(
                KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst()), null);
        SendResult<String, String> sendResultSecond = new SendResult<>(new ProducerRecord<>(
                KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()), null);
        CompletableFuture<SendResult<String, String>> futureFirst = CompletableFuture.completedFuture(sendResultFirst);
        CompletableFuture<SendResult<String, String>> futureSecond = CompletableFuture.completedFuture(sendResultSecond);

        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst()))
                .thenReturn(futureFirst);
        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()))
                .thenReturn(futureSecond);

        // when
        List<PublishResult> publishResultList = underTest.publishPaymentEvents(eventsToPublish);

        // then
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst());
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast());
        assertThat(publishResultList.size()).isEqualTo(eventIds.size());
        assertThat(publishResultList.getFirst()).satisfies(publishResult -> {
            assertThat(publishResult.getEventId()).isEqualTo(eventIds.getFirst());
            assertThat(publishResult.isPublished()).isTrue();
            assertThat(publishResult.getErrorInfo()).isNull();
        });
        assertThat(publishResultList.getLast()).satisfies(publishResult -> {
            assertThat(publishResult.getEventId()).isEqualTo(eventIds.getLast());
            assertThat(publishResult.isPublished()).isTrue();
            assertThat(publishResult.getErrorInfo()).isNull();
        });
        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + eventIds.getFirst());
        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + eventIds.getLast());
    }

    @Test
    public void shouldReturnOnePublishResultAsPublishedAndTwoPublishResultsAsNotPublished(CapturedOutput output) {
        // given
        String synchronousFailureMessage = "Synchronous send failure, id=";
        String asynchronousFailureMessage = "Asynchronous send failure, id=";
        String eventType = PaymentEventType.PAYMENT_CREATED_EVENT.name();
        List<UUID> paymentIds = List.of(generateUuid(), generateUuid(), generateUuid());
        List<UUID> eventIds = List.of(generateUuid(), generateUuid(), generateUuid());
        List<PaymentCreatedEvent> paymentCreatedEvents = getListOfPaymentCreatedEvents(paymentIds, eventIds);
        List<String> payloads = getListOfPayloads(paymentCreatedEvents);
        List<OutboxEventToPublish> eventsToPublish = getListOfOutboxEventToPublish(eventIds, paymentIds, eventType, payloads);

        SendResult<String, String> sendResultThird = new SendResult<>(new ProducerRecord<>(
                KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()), null);
        CompletableFuture<SendResult<String, String>> futureSecond = CompletableFuture.failedFuture(
                new RuntimeException(asynchronousFailureMessage + eventIds.get(1).toString()));
        CompletableFuture<SendResult<String, String>> futureThird = CompletableFuture.completedFuture(sendResultThird);

        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst()))
                .thenThrow(new IllegalStateException(synchronousFailureMessage + eventIds.getFirst().toString()));
        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.get(1).toString(), payloads.get(1)))
                .thenReturn(futureSecond); // test of asynchronous send failure
        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()))
                .thenReturn(futureThird);

        // when
        List<PublishResult> publishResultList = underTest.publishPaymentEvents(eventsToPublish);

        // then
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst());
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.get(1).toString(), payloads.get(1));
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast());
        assertThat(publishResultList.getFirst()).satisfies(publishResult -> {
            assertThat(publishResult.getEventId()).isEqualTo(eventIds.getFirst());
            assertThat(publishResult.isPublished()).isFalse();
            assertThat(publishResult.getErrorInfo()).contains(IllegalStateException.class.getName());
            assertThat(publishResult.getErrorInfo()).contains(synchronousFailureMessage);
        });
        assertThat(publishResultList.get(1)).satisfies(publishResult -> {
            assertThat(publishResult.getEventId()).isEqualTo(eventIds.get(1));
            assertThat(publishResult.isPublished()).isFalse();
            assertThat(publishResult.getErrorInfo()).contains(RuntimeException.class.getName());
            assertThat(publishResult.getErrorInfo()).contains(asynchronousFailureMessage);
        });
        assertThat(publishResultList.getLast()).satisfies(publishResult -> {
            assertThat(publishResult.getEventId()).isEqualTo(eventIds.getLast());
            assertThat(publishResult.isPublished()).isTrue();
            assertThat(publishResult.getErrorInfo()).isNull();
        });
        assertThat(output.getOut()).contains(synchronousFailureMessage + eventIds.getFirst());
        assertThat(output.getOut()).contains(asynchronousFailureMessage + eventIds.get(1));
        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + eventIds.getLast());
    }

    @Test
    public void shouldReturnEmptyList() {
        // when
        List<PublishResult> publishResultList = underTest.publishPaymentEvents(Collections.emptyList());

        // then
        verifyNoInteractions(kafkaTemplate);
        assertThat(publishResultList).isEmpty();
    }


    private List<PaymentCreatedEvent> getListOfPaymentCreatedEvents(List<UUID> paymentIds, List<UUID> eventIds) {
        List<PaymentCreatedEvent> resultList = new ArrayList<>();
        for (int i = 0; i < eventIds.size(); i++) {
            resultList.add(getPaymentCreatedEvent(
                    paymentIds.get(i), eventIds.get(i), IDEMPOTENCY_KEYS[i], VALID_AMOUNTS[i], CURRENCY_PLN)
            );
        }
        return resultList;
    }

    private List<String> getListOfPayloads(List<PaymentCreatedEvent> paymentCreatedEvents) {
        return paymentCreatedEvents.stream()
                .map(objectMapper::writeValueAsString)
                .toList();
    }

    private List<OutboxEventToPublish> getListOfOutboxEventToPublish(List<UUID> eventIds, List<UUID> paymentIds,
                                                                     String eventType, List<String> payloads) {
        List<OutboxEventToPublish> resultList = new ArrayList<>();
        for (int i = 0; i < eventIds.size(); i++) {
            resultList.add(new OutboxEventToPublish(eventIds.get(i), paymentIds.get(i), eventType, payloads.get(i)));
        }
        return resultList;
    }

    private PaymentCreatedEvent getPaymentCreatedEvent(UUID paymentId, UUID eventId, String idempotencyKey,
                                                       BigDecimal amount, String currency) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentId)
                .eventId(eventId)
                .amount(amount)
                .currency(currency)
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}