package priv.home.paymentservice.eventconsumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import priv.home.paymentservice.eventconsumer.data.ConsumedEventEntity;
import priv.home.paymentservice.eventconsumer.data.ConsumedEventStorageJpa;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PaymentEventConsumerServiceTest {


    // for unit test correctness of payload is not necessary
    private static final String SAMPLE_PAYLOAD = "{\"eventId\":\"c32d0cae-a082-4633-95c8-add8fd4a8b41\"}";

    @Mock
    private ConsumedEventStorageJpa consumedEventStorageJpa;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentEventConsumerService underTest;


    @Test
    public void shouldProceedToBusinessOperationsAndReturnConsumedEventEntity(CapturedOutput output) {
        // given
        UUID eventId = generateUuid();
        PaymentCreatedEvent paymentCreatedEvent = getValidPaymentCreatedEvent(eventId);
        ConsumedEventEntity consumedEventEntity = getConsumedEventEntity(eventId);
        when(objectMapper.readValue(SAMPLE_PAYLOAD, PaymentCreatedEvent.class))
                .thenReturn(paymentCreatedEvent);
        when(consumedEventStorageJpa.insertConsumedEventIfAbsent(eventId))
                .thenReturn(Optional.of(consumedEventEntity));

        // when
        Optional<ConsumedEventEntity> resultEventOptional = underTest.processConsumedEvent(SAMPLE_PAYLOAD);

        // then
        verify(consumedEventStorageJpa).insertConsumedEventIfAbsent(eventId);

        assertThat(resultEventOptional).isNotEmpty();
        ConsumedEventEntity resultEvent = resultEventOptional.get();
        assertThat(resultEvent.getEventId()).isEqualTo(eventId);
        assertThat(output).contains("Event accepted for business processing, eventId=" + eventId);
    }


    @Test
    public void shouldLogEventAlreadyExistAndReturnEmptyOptional(CapturedOutput output) {
        // given
        UUID eventId = generateUuid();
        PaymentCreatedEvent paymentCreatedEvent = getValidPaymentCreatedEvent(eventId);
        when(objectMapper.readValue(SAMPLE_PAYLOAD, PaymentCreatedEvent.class))
                .thenReturn(paymentCreatedEvent);
        when(consumedEventStorageJpa.insertConsumedEventIfAbsent(eventId))
                .thenReturn(Optional.empty());

        // when
        Optional<ConsumedEventEntity> resultEventOptional = underTest.processConsumedEvent(SAMPLE_PAYLOAD);

        // then
        assertThat(resultEventOptional).isEmpty();
        assertThat(output).contains("Event already exist in db, eventId=" + eventId);
    }

    @Test
    public void shouldThrowExceptionForNotValidPayload() {
        // given
        String invalidPayload = "not-json";
        when(objectMapper.readValue(invalidPayload, PaymentCreatedEvent.class))
                .thenThrow(new IllegalArgumentException("Invalid payload"));

        // when + then
        assertThatThrownBy(() -> underTest.processConsumedEvent(invalidPayload))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(consumedEventStorageJpa);
    }


    private PaymentCreatedEvent getValidPaymentCreatedEvent(UUID eventId) {
        return PaymentCreatedEvent.builder()
                .eventId(eventId)
                .paymentId(generateUuid())
                .amount(new BigDecimal("123.45"))
                .currency("PLN")
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey("abc-123")
                .build();
    }

    private ConsumedEventEntity getConsumedEventEntity(UUID eventId) {
        return ConsumedEventEntity.builder()
                .eventId(eventId)
                .consumedAt(OffsetDateTime.now())
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}