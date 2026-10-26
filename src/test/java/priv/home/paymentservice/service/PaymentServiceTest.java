package priv.home.paymentservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.application.PaymentService;
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
    private PaymentStorageJpa paymentStorageJpa;
    @Mock
    private OutboxStorageJpa outboxStorageJpa;
    @InjectMocks
    private PaymentService underTest;


    @Test
    public void shouldAddPaymentToStorage() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        // thenAnswer -> returns Payment object passed by Service to Storage

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        verify(paymentStorageJpa).insertPaymentIfAbsent(any(PaymentEntity.class));
    }

    @Test
    public void shouldAddPaymentToStorageWithCorrectValues() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        ArgumentCaptor<PaymentEntity> paymentArgumentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        // when + then
        underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);
        verify(paymentStorageJpa).insertPaymentIfAbsent(paymentArgumentCaptor.capture());
        PaymentEntity savedPayment = paymentArgumentCaptor.getValue();

        assertThat(savedPayment.getAmount()).isEqualTo(createPaymentRequest.amount());
        assertThat(savedPayment.getCurrency()).isEqualTo(createPaymentRequest.currency());
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(savedPayment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(savedPayment.getPaymentId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithRequestedAmountAndCurrency() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        // when
        CreatePaymentResult createPaymentResult = underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        assertThat(createPaymentResult.getAmount()).isEqualTo(createPaymentRequest.amount());
        assertThat(createPaymentResult.getCurrency()).isEqualTo(createPaymentRequest.currency());
    }

    @Test
    public void shouldGenerateIdForNewPayment() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        // when
        CreatePaymentResult paymentResponseResult = underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        assertThat(paymentResponseResult.getPaymentId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithCreatedStatus() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));

        // when
        CreatePaymentResult paymentResponseResult = underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        assertThat(paymentResponseResult.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    public void shouldReturnExistingPaymentFromStorage() {
        // given
        CreatePaymentRequest validCreatePaymentRequest = getValidPaymentRequest();
        PaymentEntity validPayment = getValidPaymentFromRequest(validCreatePaymentRequest);
        when(paymentStorageJpa.findById(any(UUID.class))).thenReturn(Optional.of(validPayment));

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
        when(paymentStorageJpa.findById(any(UUID.class))).thenReturn(Optional.empty());

        // when
        Optional<PaymentResponse> result = underTest.retrieveSinglePaymentByPaymentId(generateUuid());

        // then
        assertThat(result).isEmpty();
    }


    @Test
    public void shouldThrowIllegalStateExceptionInPaymentCreate() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenReturn(Optional.empty());

        // when + then
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest));
        assertThat(thrown.getMessage()).isEqualTo(PAYMENT_NOT_FOUND_AFTER_CONFLICT);
    }

    @Test
    public void shouldNotInsertPaymentOrCreateOutboxEventWhenPaymentAlreadyExists() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        PaymentEntity payment = getValidPaymentFromRequest(createPaymentRequest);
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.of(payment));

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        verify(paymentStorageJpa, never()).insertPaymentIfAbsent(any(PaymentEntity.class));
        verifyNoInteractions(outboxStorageJpa);
    }

    @Test
    public void shouldNotCreateOutboxEventWhenInsertOnConflict() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        PaymentEntity payment = getValidPaymentFromRequest(createPaymentRequest);
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenReturn(Optional.empty());

        // when
        CreatePaymentResult createPaymentResultResult = underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        verifyNoInteractions(outboxStorageJpa);
        assertThat(createPaymentResultResult.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(createPaymentResultResult.isWasAlreadyInDb()).isTrue();
    }


    @Test
    public void shouldCallOutboxStorageForPositivePaymentCreation() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        PaymentEntity payment = getValidPaymentFromRequest(createPaymentRequest);
        ArgumentCaptor<PaymentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        String payload = """
                {"paymentId":"%s","amount":123.45,"currency":"PLN","paymentStatus":"CREATED","idempotencyKey":"%s"}
                """.formatted(payment.getPaymentId(), IDEMPOTENCY_KEY).trim();
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenReturn(Optional.of(payment));
        when(objectMapper.writeValueAsString(any(PaymentCreatedEvent.class)))
                .thenReturn(payload);

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, createPaymentRequest);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        // then
        PaymentCreatedEvent capturedEvent = eventCaptor.getValue();
        verify(outboxStorageJpa, times(1)).save(any(OutboxEventEntity.class));
        assertThat(capturedEvent.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(capturedEvent.getAmount()).isEqualByComparingTo(payment.getAmount());
        assertThat(capturedEvent.getCurrency()).isEqualTo(payment.getCurrency());
        assertThat(capturedEvent.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }


    private PaymentEntity getValidPaymentFromRequest(CreatePaymentRequest validCreatePaymentRequest) {
        return PaymentEntity.builder()
                .paymentId(generateUuid())
                .amount(validCreatePaymentRequest.amount())
                .currency(validCreatePaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private CreatePaymentRequest getValidPaymentRequest() {
        return new CreatePaymentRequest(new BigDecimal("123.45"), "PLN");
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}