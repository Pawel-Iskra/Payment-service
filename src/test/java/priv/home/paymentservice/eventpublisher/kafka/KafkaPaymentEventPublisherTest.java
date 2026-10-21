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
import priv.home.paymentservice.eventpublisher.model.OutboxEventToPublish;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.PaymentEventType;
import priv.home.paymentservice.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
    private static final BigDecimal VALID_AMOUNT_FIRST = new BigDecimal("123.45");
    private static final BigDecimal VALID_AMOUNT_SECOND = new BigDecimal("223.45");
    private static final BigDecimal VALID_AMOUNT_THIRD = new BigDecimal("323.45");
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
    public void shouldReturnAllEventIds(CapturedOutput output) {
        // given
        String eventType = PaymentEventType.PAYMENT_CREATED_EVENT.name();
        List<UUID> paymentIds = List.of(generateUuid(), generateUuid());
        List<UUID> eventIds = List.of(generateUuid(), generateUuid());
        List<PaymentCreatedEvent> paymentCreatedEvents = List.of(
                getPaymentCreatedEvent(paymentIds.getFirst(), eventIds.getFirst(), IDEMPOTENCY_KEY_FIRST, VALID_AMOUNT_FIRST, CURRENCY_PLN),
                getPaymentCreatedEvent(paymentIds.getLast(), eventIds.getLast(), IDEMPOTENCY_KEY_SECOND, VALID_AMOUNT_SECOND, CURRENCY_PLN));
        List<String> payloads = List.of(
                objectMapper.writeValueAsString(paymentCreatedEvents.getFirst()),
                objectMapper.writeValueAsString(paymentCreatedEvents.getLast()));
        List<OutboxEventToPublish> eventsToPublish = List.of(
                new OutboxEventToPublish(eventIds.getFirst(), paymentIds.getFirst(), eventType, payloads.getFirst()),
                new OutboxEventToPublish(eventIds.getLast(), paymentIds.getLast(), eventType, payloads.getLast()));
        SendResult<String, String> sendResultFirst = new SendResult<>(new ProducerRecord<>(
                KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst()), null);
        CompletableFuture<SendResult<String, String>> futureFirst = CompletableFuture.completedFuture(sendResultFirst);
        SendResult<String, String> sendResultSecond = new SendResult<>(new ProducerRecord<>(
                KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()), null);
        CompletableFuture<SendResult<String, String>> futureSecond = CompletableFuture.completedFuture(sendResultSecond);

        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst()))
                .thenReturn(futureFirst);
        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()))
                .thenReturn(futureSecond);

        // when
        List<UUID> idsOfPublishedEvents = underTest.publishPaymentEvents(eventsToPublish);

        // then
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst());
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast());
        assertThat(idsOfPublishedEvents.size()).isEqualTo(eventIds.size());
        assertThat(idsOfPublishedEvents).containsAll(eventIds);
        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + eventIds.getFirst());
        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + eventIds.getLast());
    }

    @Test
    public void shouldReturnOnlyIdsOfSuccessfullyPublishedEvents(CapturedOutput output) {
        // given
        String synchronousFailureMessage = "Synchronous send failure, id=";
        String asynchronousFailureMessage = "Asynchronous send failure, id=";
        String eventType = PaymentEventType.PAYMENT_CREATED_EVENT.name();
        List<UUID> paymentIds = List.of(generateUuid(), generateUuid(), generateUuid());
        List<UUID> eventIds = List.of(generateUuid(), generateUuid(), generateUuid());
        List<PaymentCreatedEvent> paymentCreatedEvents = List.of(
                getPaymentCreatedEvent(paymentIds.getFirst(), eventIds.getFirst(), IDEMPOTENCY_KEY_FIRST, VALID_AMOUNT_FIRST, CURRENCY_PLN),
                getPaymentCreatedEvent(paymentIds.get(1), eventIds.get(1), IDEMPOTENCY_KEY_SECOND, VALID_AMOUNT_SECOND, CURRENCY_PLN),
                getPaymentCreatedEvent(paymentIds.getLast(), eventIds.getLast(), IDEMPOTENCY_KEY_THIRD, VALID_AMOUNT_THIRD, CURRENCY_PLN));
        List<String> payloads = List.of(
                objectMapper.writeValueAsString(paymentCreatedEvents.getFirst()),
                objectMapper.writeValueAsString(paymentCreatedEvents.get(1)),
                objectMapper.writeValueAsString(paymentCreatedEvents.getLast()));
        List<OutboxEventToPublish> eventsToPublish = List.of(
                new OutboxEventToPublish(eventIds.getFirst(), paymentIds.getFirst(), eventType, payloads.getFirst()),
                new OutboxEventToPublish(eventIds.get(1), paymentIds.get(1), eventType, payloads.get(1)),
                new OutboxEventToPublish(eventIds.getLast(), paymentIds.getLast(), eventType, payloads.getLast()));

        CompletableFuture<SendResult<String, String>> futureSecond =
                CompletableFuture.failedFuture(new RuntimeException(asynchronousFailureMessage + eventIds.get(1).toString()));
        SendResult<String, String> sendResultThird = new SendResult<>(new ProducerRecord<>(
                KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()), null);
        CompletableFuture<SendResult<String, String>> futureThird = CompletableFuture.completedFuture(sendResultThird);

        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst()))
                .thenThrow(new IllegalStateException(synchronousFailureMessage + eventIds.getFirst().toString()));
        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.get(1).toString(), payloads.get(1)))
                .thenReturn(futureSecond); // test of asynchronous send failure
        when(kafkaTemplate.send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast()))
                .thenReturn(futureThird);

        // when
        List<UUID> idsOfPublishedEvents = underTest.publishPaymentEvents(eventsToPublish);

        // then
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getFirst().toString(), payloads.getFirst());
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.get(1).toString(), payloads.get(1));
        verify(kafkaTemplate).send(KAFKA_TOPIC, paymentIds.getLast().toString(), payloads.getLast());
        assertThat(idsOfPublishedEvents.size()).isEqualTo(eventIds.size() - 2);
        assertThat(idsOfPublishedEvents).doesNotContain(eventIds.getFirst());
        assertThat(idsOfPublishedEvents).doesNotContain(eventIds.get(1));
        assertThat(idsOfPublishedEvents).contains(eventIds.getLast());
        assertThat(output.getOut()).contains(synchronousFailureMessage + eventIds.getFirst());
        assertThat(output.getOut()).contains(asynchronousFailureMessage + eventIds.get(1));
        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + eventIds.getLast());
    }

    @Test
    public void shouldReturnEmptyList() {
        // when
        List<UUID> idsOfPublishedEvents = underTest.publishPaymentEvents(Collections.emptyList());

        // then
        verifyNoInteractions(kafkaTemplate);
        assertThat(idsOfPublishedEvents).isEmpty();
    }


    private PaymentCreatedEvent getPaymentCreatedEvent(UUID paymentId, UUID eventId, String idempotencyKey, BigDecimal amount, String currency) {
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