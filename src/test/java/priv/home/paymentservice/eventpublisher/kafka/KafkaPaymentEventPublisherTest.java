package priv.home.paymentservice.eventpublisher.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class KafkaPaymentEventPublisherTest {

    private static final String IDEMPOTENCY_KEY = "abc-123";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String KAFKA_TOPIC = "payment-created";
    private static final String ERROR_WHILE_PUBLISHING_EVENT = "Error while publishing PaymentCreatedEvent, paymentId=";
    private static final String EVENT_PUBLISHED_SUCCESSFULLY = "PaymentCreatedEvent successfully acknowledged by Kafka, paymentId=";
    private static final String PUBLISHING_INITIATION_FAILED = "Failed to initiate publishing of PaymentCreatedEvent, paymentId=";

    @Mock
    private KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;
    private KafkaPaymentEventPublisher underTest;


    @BeforeEach
    public void setUp() {
        underTest = new KafkaPaymentEventPublisher(KAFKA_TOPIC, kafkaTemplate);
    }


    @Test
    public void shouldHandleCompletableFutureWhenEventSendSuccessfully(CapturedOutput output) {
        // given
        UUID paymentId = generateUuid();
        Payment payment = buildPayment(paymentId, VALID_AMOUNT, CURRENCY_PLN);
        CompletableFuture<SendResult<String, PaymentCreatedEvent>> completableFutureResult = new CompletableFuture<>();
        SendResult<String, PaymentCreatedEvent> sendResult = Mockito.mock(SendResult.class);
        Mockito.when(kafkaTemplate.send(any(String.class), any(String.class), any(PaymentCreatedEvent.class)))
                .thenReturn(completableFutureResult);
        ArgumentCaptor<PaymentCreatedEvent> paymentCreatedEventArgCpt = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        ArgumentCaptor<String> kafkaTopicArgCpt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> paymentIdArgCpt = ArgumentCaptor.forClass(String.class);

        // when
        underTest.publish(IDEMPOTENCY_KEY, payment);
        verify(kafkaTemplate).send(kafkaTopicArgCpt.capture(), paymentIdArgCpt.capture(), paymentCreatedEventArgCpt.capture());
        completableFutureResult.complete(sendResult);

        // then
        PaymentCreatedEvent paymentCreatedEventToSend = paymentCreatedEventArgCpt.getValue();
        String kafkaTopicWhileSending = kafkaTopicArgCpt.getValue();
        String paymentIdWhileSending = paymentIdArgCpt.getValue();

        assertThat(output.getOut()).contains(EVENT_PUBLISHED_SUCCESSFULLY + paymentId);
        assertThat(paymentCreatedEventToSend.getPaymentId()).isEqualTo(paymentId);
        assertThat(paymentCreatedEventToSend.getAmount()).isEqualTo(VALID_AMOUNT);
        assertThat(paymentCreatedEventToSend.getCurrency()).isEqualTo(CURRENCY_PLN);
        assertThat(paymentCreatedEventToSend.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentCreatedEventToSend.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(kafkaTopicWhileSending).isEqualTo(KAFKA_TOPIC);
        assertThat(paymentIdWhileSending).isEqualTo(paymentId.toString());
    }

    @Test
    public void shouldLogErrorWhenEventSendCompletedExceptionally(CapturedOutput output) {
        // given
        UUID paymentId = generateUuid();
        Payment payment = buildPayment(paymentId, VALID_AMOUNT, CURRENCY_PLN);
        CompletableFuture<SendResult<String, PaymentCreatedEvent>> completableFutureResult = new CompletableFuture<>();
        Mockito.when(kafkaTemplate.send(any(String.class), any(String.class), any(PaymentCreatedEvent.class)))
                .thenReturn(completableFutureResult);
        String exceptionMessage = "Kafka broker unavailable";

        // when
        underTest.publish(IDEMPOTENCY_KEY, payment);
        completableFutureResult.completeExceptionally(new RuntimeException(exceptionMessage));

        // then
        assertThat(output.getOut())
                .contains(ERROR_WHILE_PUBLISHING_EVENT + paymentId)
                .contains(RuntimeException.class.getCanonicalName())
                .contains(exceptionMessage);
    }

    @Test
    public void shouldLogErrorWhenSendThrowsException(CapturedOutput output) {
        // given
        UUID paymentId = generateUuid();
        Payment payment = buildPayment(paymentId, VALID_AMOUNT, CURRENCY_PLN);
        String exceptionMessage = "Error occurred while kafkaTemplate.send()";
        Mockito.when(kafkaTemplate.send(any(String.class), any(String.class), any(PaymentCreatedEvent.class)))
                .thenThrow(new RuntimeException(exceptionMessage));

        // when
        underTest.publish(IDEMPOTENCY_KEY, payment);

        // then
        assertThat(output.getOut())
                .contains(PUBLISHING_INITIATION_FAILED + paymentId)
                .contains(RuntimeException.class.getCanonicalName())
                .contains(exceptionMessage);
    }


    private Payment buildPayment(UUID paymentId, BigDecimal amount, String currency) {
        return Payment.builder()
                .paymentId(paymentId)
                .amount(amount)
                .currency(currency)
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}