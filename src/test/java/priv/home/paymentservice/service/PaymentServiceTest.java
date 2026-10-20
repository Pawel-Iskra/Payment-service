package priv.home.paymentservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import priv.home.paymentservice.data.jdbcoption.OutboxStorageJdbc;
import priv.home.paymentservice.data.jdbcoption.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.data.jdbcoption.Payment;
import priv.home.paymentservice.dto.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String IDEMPOTENCY_KEY = "abc-123";
    private static final String PAYMENT_NOT_FOUND_AFTER_CONFLICT = "Payment not found after idempotency conflict";

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PaymentStorageJdbc paymentStorageJdbc;
    @Mock
    private OutboxStorageJdbc outboxStorageJdbc;
    @InjectMocks
    private PaymentService underTest;


    @Test
    public void shouldAddPaymentToStorage() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
        // thenAnswer -> zwraca obiekt Payment, który serwis przekazał do storage

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        verify(paymentStorageJdbc).insertPaymentIfAbsent(any(String.class), any(Payment.class));
    }

    @Test
    public void shouldAddPaymentToStorageWithCorrectValues() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        ArgumentCaptor<Payment> paymentArgumentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
        // thenAnswer -> zwraca obiekt Payment, który serwis przekazał do storage

        // when + then
        underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);
        verify(paymentStorageJdbc).insertPaymentIfAbsent(any(String.class), paymentArgumentCaptor.capture());
        Payment savedPayment = paymentArgumentCaptor.getValue();

        assertThat(savedPayment.getAmount()).isEqualTo(paymentRequest.amount());
        assertThat(savedPayment.getCurrency()).isEqualTo(paymentRequest.currency());
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(savedPayment.getPaymentId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithRequestedAmountAndCurrency() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        // when
        PaymentCreationDto paymentCreationDto = underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        assertThat(paymentCreationDto.getAmount()).isEqualTo(paymentRequest.amount());
        assertThat(paymentCreationDto.getCurrency()).isEqualTo(paymentRequest.currency());
    }

    @Test
    public void shouldGenerateIdForNewPayment() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        // when
        PaymentCreationDto paymentResponseResult = underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        assertThat(paymentResponseResult.getPaymentId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithCreatedStatus() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

        // when
        PaymentCreationDto paymentResponseResult = underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        assertThat(paymentResponseResult.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    public void shouldReturnExistingPaymentFromStorage() {
        // given
        PaymentRequest validPaymentRequest = getValidPaymentRequest();
        Payment validPayment = getValidPaymentFromRequest(validPaymentRequest);
        when(paymentStorageJdbc.retrievePaymentByPaymentId(any())).thenReturn(Optional.of(validPayment));

        // when
        Optional<PaymentResponse> paymentResponseOptional = underTest.retrieveSinglePaymentByPaymentId(validPayment.getPaymentId());

        // then
        assertThat(paymentResponseOptional).isNotEmpty();
        assertThat(paymentResponseOptional.get().getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(paymentResponseOptional.get().getCurrency()).isEqualTo(validPayment.getCurrency());
        assertThat(paymentResponseOptional.get().getAmount()).isEqualTo(validPayment.getAmount());
    }

    @Test
    public void shouldReturnEmptyOptionalForNotExistingPayment() {
        // given
        when(paymentStorageJdbc.retrievePaymentByPaymentId(any())).thenReturn(Optional.empty());

        // when
        Optional<PaymentResponse> result = underTest.retrieveSinglePaymentByPaymentId(generateUuid());

        // then
        assertThat(result).isEmpty();
    }


    @Test
    public void shouldThrowIllegalStateExceptionInPaymentCreate() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenReturn(Optional.empty());

        // when + then
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest));
        assertThat(thrown.getMessage()).isEqualTo(PAYMENT_NOT_FOUND_AFTER_CONFLICT);
    }

    @Test
    public void shouldNotInsertPaymentOrCreateOutboxEventWhenPaymentAlreadyExists() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        Payment payment = getValidPaymentFromRequest(paymentRequest);
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any()))
                .thenReturn(Optional.of(payment));

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        verify(paymentStorageJdbc, never()).insertPaymentIfAbsent(any(String.class), any(Payment.class));
        verifyNoInteractions(outboxStorageJdbc);
    }

    @Test
    public void shouldNotCreateOutboxEventWhenInsertOnConflict() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        Payment payment = getValidPaymentFromRequest(paymentRequest);
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenReturn(Optional.empty());

        // when
        PaymentCreationDto paymentCreationDtoResult = underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        verifyNoInteractions(outboxStorageJdbc);
        assertThat(paymentCreationDtoResult.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(paymentCreationDtoResult.isWasAlreadyInDb()).isTrue();
    }


    @Test
    public void shouldCallOutboxStorageForPositivePaymentCreation() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        Payment payment = getValidPaymentFromRequest(paymentRequest);
        ArgumentCaptor<PaymentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        String payload = """
                {"paymentId":"%s","amount":123.45,"currency":"PLN","paymentStatus":"CREATED","idempotencyKey":"%s"}
                """.formatted(payment.getPaymentId(), IDEMPOTENCY_KEY).trim();
        when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJdbc.insertPaymentIfAbsent(any(String.class), any(Payment.class)))
                .thenReturn(Optional.of(payment));
        when(objectMapper.writeValueAsString(any(PaymentCreatedEvent.class)))
                .thenReturn(payload);

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        // then
        PaymentCreatedEvent capturedEvent = eventCaptor.getValue();
        verify(outboxStorageJdbc, times(1))
                .addPaymentEventToOutbox(any(UUID.class), any(UUID.class), any(String.class), any(String.class));
        assertThat(capturedEvent.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(capturedEvent.getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(capturedEvent.getCurrency()).isEqualTo(payment.getCurrency());
        assertThat(capturedEvent.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }


    private Payment getValidPaymentFromRequest(PaymentRequest validPaymentRequest) {
        return Payment.builder()
                .paymentId(generateUuid())
                .amount(validPaymentRequest.amount())
                .currency(validPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(String idempotencyKey, Payment paymentToPublish) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentToPublish.getPaymentId())
                .amount(paymentToPublish.getAmount())
                .currency(paymentToPublish.getCurrency())
                .paymentStatus(paymentToPublish.getPaymentStatus())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private PaymentRequest getValidPaymentRequest() {
        return new PaymentRequest(new BigDecimal("123.45"), "PLN");
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}