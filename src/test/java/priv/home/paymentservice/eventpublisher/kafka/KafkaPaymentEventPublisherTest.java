package priv.home.paymentservice.eventpublisher.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaPaymentEventPublisherTest {

    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String KAFKA_TOPIC = "payment-created";
    private static final String FAILED_TO_PUBLISH_EVENT = "Failed to publish PaymentCreatedEvent with id=";
    private static final String EVENT_PUBLISHED = "PaymentCreatedEvent published, paymentId=";

    @Mock
    private KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;
    private KafkaPaymentEventPublisher underTest;


    @BeforeEach
    public void setUp() {
        underTest = new KafkaPaymentEventPublisher(KAFKA_TOPIC, kafkaTemplate);
    }


    @Test
    public void shouldHandleCompletableFutureWhenEventSend(CapturedOutput output) {
        // given
        UUID paymentId = generateUuid();
        Payment payment = getValidPayment(paymentId);
        CompletableFuture<SendResult<String, PaymentCreatedEvent>> completableFutureResult = new CompletableFuture<>();
        SendResult<String, PaymentCreatedEvent> sendResult = Mockito.mock(SendResult.class);
        Mockito.when(kafkaTemplate.send(any(String.class), any(String.class), any(PaymentCreatedEvent.class)))
                .thenReturn(completableFutureResult);

        // when
        underTest.publish(paymentId.toString(), payment);
        completableFutureResult.complete(sendResult);

        // then
        assertThat(output.getOut()).contains(EVENT_PUBLISHED + paymentId);
    }

    @Test
    public void shouldLogErrorWhenExceptionWhileEventSent(CapturedOutput output) {
        // given
        UUID paymentId = generateUuid();
        Payment payment = getValidPayment(paymentId);
        CompletableFuture<SendResult<String, PaymentCreatedEvent>> completableFutureResult = new CompletableFuture<>();
        Mockito.when(kafkaTemplate.send(any(String.class), any(String.class), any(PaymentCreatedEvent.class)))
                .thenReturn(completableFutureResult);

        // when
        underTest.publish(paymentId.toString(), payment);
        completableFutureResult.completeExceptionally(new RuntimeException());

        // then
        assertThat(output.getOut()).contains(FAILED_TO_PUBLISH_EVENT + paymentId);
    }


    private Payment getValidPayment(UUID paymentId) {
        return Payment.builder()
                .paymentId(paymentId)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}