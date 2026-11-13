package priv.home.paymentservice.payment.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.CreditorRequest;
import priv.home.paymentservice.payment.api.dto.DebtorRequest;
import priv.home.paymentservice.payment.api.logging.PaymentPersistedEventListener;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;
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

    private static final UUID CORRELATION_ID = UUID.fromString("e0eb0678-c0cc-48b6-9be7-c55c68e25cdb");
    private static final String IDEMPOTENCY_KEY = "abc-123";
    private static final String PAYMENT_NOT_FOUND_AFTER_CONFLICT = "Payment not found after idempotency conflict";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("1234.56");
    private static final String CURRENCY_PLN = "PLN";
    private static final String END_TO_END_ID = "E2E-20261108-001";
    private static final String DEBTOR_IBAN = "PL93109024023152899461927776";
    private static final String CREDITOR_IBAN = "PL82109024029148361863856384";
    private static final DebtorRequest DEBTOR_REQUEST = new DebtorRequest("Debtor Name", DEBTOR_IBAN);
    private static final CreditorRequest CREDITOR_REQUEST = new CreditorRequest("Creditor Name", CREDITOR_IBAN);

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private PaymentStorageJpa paymentStorageJpa;
    @Mock
    private OutboxStorageJpa outboxStorageJpa;
    @Mock
    private PaymentPersistedEventListener persistedEventListener;
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
        underTest.createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);

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
        underTest.createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);
        verify(paymentStorageJpa).insertPaymentIfAbsent(paymentArgumentCaptor.capture());
        PaymentEntity savedPayment = paymentArgumentCaptor.getValue();

        assertThat(savedPayment.getAmount()).isEqualTo(createPaymentRequest.amount());
        assertThat(savedPayment.getCurrency()).isEqualTo(createPaymentRequest.currency());
        assertThat(savedPayment.getEndToEndId()).isEqualTo(createPaymentRequest.endToEndId());
        assertThat(savedPayment.getDebtorIban()).isEqualTo(createPaymentRequest.debtorRequest().debtorIban());
        assertThat(savedPayment.getCreditorIban()).isEqualTo(createPaymentRequest.creditorRequest().creditorIban());
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
        CreatePaymentResult createPaymentResult = underTest
                .createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        assertThat(createPaymentResult.amount()).isEqualTo(createPaymentRequest.amount());
        assertThat(createPaymentResult.currency()).isEqualTo(createPaymentRequest.currency());
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
        CreatePaymentResult paymentResponseResult = underTest
                .createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        assertThat(paymentResponseResult.paymentId()).isNotNull();
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
        CreatePaymentResult paymentResponseResult = underTest
                .createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        assertThat(paymentResponseResult.paymentStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    public void shouldReturnExistingPaymentFromStorage() {
        // given
        CreatePaymentRequest validCreatePaymentRequest = getValidPaymentRequest();
        PaymentEntity validPayment = getValidPaymentFromRequest(validCreatePaymentRequest);
        when(paymentStorageJpa.findById(any(UUID.class)))
                .thenReturn(Optional.of(validPayment));

        // when
        Optional<PaymentRetrieveResult> paymentRetrieveResponseOptional = underTest
                .retrieveSinglePaymentByPaymentId(validPayment.getPaymentId());

        // then
        assertThat(paymentRetrieveResponseOptional).isNotEmpty();
        PaymentRetrieveResult retrieveResult = paymentRetrieveResponseOptional.get();
        assertThat(retrieveResult.paymentStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(retrieveResult.endToEndId()).isEqualTo(validPayment.getEndToEndId());
        assertThat(retrieveResult.debtorIban()).isEqualTo(validPayment.getDebtorIban());
        assertThat(retrieveResult.creditorIban()).isEqualTo(validPayment.getCreditorIban());
        assertThat(retrieveResult.currency()).isEqualTo(validPayment.getCurrency());
        assertThat(retrieveResult.amount()).isEqualTo(validPayment.getAmount());
    }

    @Test
    public void shouldReturnEmptyOptionalForNotExistingPayment() {
        // given
        when(paymentStorageJpa.findById(any(UUID.class))).thenReturn(Optional.empty());

        // when
        Optional<PaymentRetrieveResult> result = underTest.retrieveSinglePaymentByPaymentId(generateUuid());

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
                () -> underTest.createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest));
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
        underTest.createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);

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
        CreatePaymentResult createPaymentResultResult = underTest
                .createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);

        // then
        verifyNoInteractions(outboxStorageJpa);
        assertThat(createPaymentResultResult.paymentId()).isEqualTo(payment.getPaymentId());
        assertThat(createPaymentResultResult.wasAlreadyInDb()).isTrue();
    }


    @Test
    public void shouldCallOutboxStorageForPositivePaymentCreation() {
        // given
        CreatePaymentRequest createPaymentRequest = getValidPaymentRequest();
        PaymentEntity payment = getValidPaymentFromRequest(createPaymentRequest);
        PaymentCreatedEvent paymentCreatedEvent = getPaymentCreatedEventFromPayment(
                generateUuid(), IDEMPOTENCY_KEY, payment);
        ArgumentCaptor<PaymentCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCreatedEvent.class);
        String payload = generatePayload(paymentCreatedEvent);
        when(paymentStorageJpa.findByIdempotencyKey(any(String.class)))
                .thenReturn(Optional.empty());
        when(paymentStorageJpa.insertPaymentIfAbsent(any(PaymentEntity.class)))
                .thenReturn(Optional.of(payment));
        when(objectMapper.writeValueAsString(any(PaymentCreatedEvent.class)))
                .thenReturn(payload);

        // when
        underTest.createPayment(CORRELATION_ID, IDEMPOTENCY_KEY, createPaymentRequest);
        verify(objectMapper).writeValueAsString(eventCaptor.capture());

        // then
        PaymentCreatedEvent capturedEvent = eventCaptor.getValue();
        verify(outboxStorageJpa, times(1)).save(any(OutboxEventEntity.class));
        assertThat(capturedEvent.paymentId()).isEqualTo(payment.getPaymentId());
        assertThat(capturedEvent.amount()).isEqualByComparingTo(payment.getAmount());
        assertThat(capturedEvent.currency()).isEqualTo(payment.getCurrency());
        assertThat(capturedEvent.endToEndId()).isEqualTo(payment.getEndToEndId());
        assertThat(capturedEvent.debtorIban()).isEqualTo(payment.getDebtorIban());
        assertThat(capturedEvent.creditorIban()).isEqualTo(payment.getCreditorIban());
        assertThat(capturedEvent.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }


    private PaymentEntity getValidPaymentFromRequest(CreatePaymentRequest validCreatePaymentRequest) {
        return PaymentEntity.builder()
                .paymentId(generateUuid())
                .endToEndId(validCreatePaymentRequest.endToEndId())
                .debtorIban(validCreatePaymentRequest.debtorRequest().debtorIban())
                .creditorIban(validCreatePaymentRequest.creditorRequest().creditorIban())
                .amount(validCreatePaymentRequest.amount())
                .currency(validCreatePaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private CreatePaymentRequest getValidPaymentRequest() {
        return CreatePaymentRequest.builder()
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .debtorRequest(DEBTOR_REQUEST)
                .creditorRequest(CREDITOR_REQUEST)
                .endToEndId(END_TO_END_ID)
                .build();
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(UUID eventId, String idempotencyKey, PaymentEntity paymentEntity) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentEntity.getPaymentId())
                .eventId(eventId)
                .endToEndId(paymentEntity.getEndToEndId())
                .debtorIban(paymentEntity.getDebtorIban())
                .creditorIban(paymentEntity.getCreditorIban())
                .amount(paymentEntity.getAmount())
                .currency(paymentEntity.getCurrency())
                .paymentStatus(paymentEntity.getPaymentStatus())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private String generatePayload(PaymentCreatedEvent paymentCreatedEvent) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(paymentCreatedEvent);
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}