package priv.home.paymentservice.eventconsumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import priv.home.paymentservice.eventconsumer.data.ConsumedEventEntity;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaPaymentEventConsumerTest {

    private static final String PAYMENT_EVENT_CONSUMED = "Consumed payment event=%s";
    private static final String EVENT_SUCCESSFULLY_PROCESSED = "Payment event successfully processed, consumedEventEntity=%s";
    private static final String EVENT_NOT_PROCESSED = "Payment event was not processed (duplicate or already registered)";


    @Mock
    private PaymentEventConsumerService paymentEventConsumerService;
    @InjectMocks
    private KafkaPaymentEventConsumer underTest;

    private final ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void shouldPassPayloadAndLogSuccessMessage(CapturedOutput output) {
        // given
        UUID eventId = generateRandomUuid();
        String payload = getPayloadForConsumption(eventId);
        ConsumedEventEntity insertedEvent = getValidConsumedEventEntity(eventId);
        when(paymentEventConsumerService.processConsumedEvent(payload))
                .thenReturn(Optional.of(insertedEvent));

        // when
        underTest.consumePaymentEventFromKafka(payload);

        // then
        verify(paymentEventConsumerService).processConsumedEvent(payload);
        assertThat(output).contains(PAYMENT_EVENT_CONSUMED.formatted(payload));
        assertThat(output).contains(EVENT_SUCCESSFULLY_PROCESSED.formatted(insertedEvent));
    }

    @Test
    public void shouldPassPayloadAndLogNotProcessedMessage(CapturedOutput output) {
        // given
        UUID eventId = generateRandomUuid();
        String payload = getPayloadForConsumption(eventId);
        when(paymentEventConsumerService.processConsumedEvent(payload))
                .thenReturn(Optional.empty());

        // when
        underTest.consumePaymentEventFromKafka(payload);

        // then
        verify(paymentEventConsumerService).processConsumedEvent(payload);
        assertThat(output).contains(PAYMENT_EVENT_CONSUMED.formatted(payload));
        assertThat(output).contains(EVENT_NOT_PROCESSED);
    }

    @Test
    public void shouldPropagateServiceException(CapturedOutput output) {
        // given
        String payload = "not-json";
        String exceptionMessage = "Processing failed";
        when(paymentEventConsumerService.processConsumedEvent(payload))
                .thenThrow(new IllegalStateException(exceptionMessage));

        // when
        assertThatThrownBy(() -> underTest.consumePaymentEventFromKafka(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(exceptionMessage);

        // then
        verify(paymentEventConsumerService).processConsumedEvent(payload);
        assertThat(output).contains(PAYMENT_EVENT_CONSUMED.formatted(payload));
    }


    // here payload is json-string format of PaymentCreatedEvent
    private String getPayloadForConsumption(UUID eventId) {
        PaymentCreatedEvent paymentCreatedEvent = getValidPaymentCreatedEvent(eventId);
        return objectMapper.writeValueAsString(paymentCreatedEvent);
    }

    private PaymentCreatedEvent getValidPaymentCreatedEvent(UUID eventId) {
        return PaymentCreatedEvent.builder()
                .paymentId(generateRandomUuid())
                .eventId(eventId)
                .amount(new BigDecimal("123.45"))
                .currency("PLN")
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey("abcd-1234")
                .build();
    }

    private ConsumedEventEntity getValidConsumedEventEntity(UUID eventId) {
        return ConsumedEventEntity.builder()
                .eventId(eventId)
                .consumedAt(OffsetDateTime.now())
                .build();
    }

    private UUID generateRandomUuid() {
        return UUID.randomUUID();
    }


}