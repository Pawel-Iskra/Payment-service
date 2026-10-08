package priv.home.paymentservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String IDEMPOTENCY_KEY = "abc-123";
    private static final String DUPLICATE_IDEMPOTENCY_KEY = "Duplicate idempotency key";

    @Mock
    private PaymentStorageJdbc paymentStorageJdbc;
    @InjectMocks
    private PaymentService underTest;


    @Test
    public void shouldAddPaymentToStorage() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        verify(paymentStorageJdbc).addPaymentToStorage(any(String.class), any(Payment.class));
    }

    @Test
    public void shouldAddPaymentToStorageWithCorrectValues() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        ArgumentCaptor<Payment> paymentArgumentCaptor = ArgumentCaptor.forClass(Payment.class);

        // when + then
        underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);
        verify(paymentStorageJdbc).addPaymentToStorage(any(String.class), paymentArgumentCaptor.capture());
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

        // when
        PaymentCreationDto paymentResponseResult = underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        assertThat(paymentResponseResult.getPaymentId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithCreatedStatus() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

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
        Mockito.when(paymentStorageJdbc.retrievePaymentByPaymentId(any())).thenReturn(Optional.of(validPayment));

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
        Mockito.when(paymentStorageJdbc.retrievePaymentByPaymentId(any())).thenReturn(Optional.empty());

        // when
        Optional<PaymentResponse> result = underTest.retrieveSinglePaymentByPaymentId(generateUuid());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldHandleDuplicateKeyExceptionForPaymentCreate() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        Payment payment = getValidPaymentFromRequest(paymentRequest);
        Mockito.when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment));
        Mockito.doThrow(new DuplicateKeyException(DUPLICATE_IDEMPOTENCY_KEY))
                .when(paymentStorageJdbc).addPaymentToStorage(any(), any());

        // when
        PaymentCreationDto paymentCreationDto = underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest);

        // then
        assertThat(paymentCreationDto.getAmount()).isEqualTo(payment.getAmount());
        assertThat(paymentCreationDto.getCurrency()).isEqualTo(payment.getCurrency());
        assertThat(paymentCreationDto.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(paymentCreationDto.getPaymentStatus()).isEqualTo(payment.getPaymentStatus());
        assertThat(paymentCreationDto.isWasAlreadyInDb()).isTrue();
    }

    @Test
    public void shouldThrowDuplicateKeyExceptionForPaymentCreate() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        Mockito.when(paymentStorageJdbc.retrievePaymentByIdempotencyKey(any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        Mockito.doThrow(new DuplicateKeyException(DUPLICATE_IDEMPOTENCY_KEY))
                .when(paymentStorageJdbc).addPaymentToStorage(any(), any());

        // when + then
        DuplicateKeyException thrown = assertThrows(DuplicateKeyException.class, () ->
                underTest.createPayment(IDEMPOTENCY_KEY, paymentRequest));
        assertThat(thrown.getMessage()).isEqualTo(DUPLICATE_IDEMPOTENCY_KEY);
    }


    private Payment getValidPaymentFromRequest(PaymentRequest validPaymentRequest) {
        return Payment.builder()
                .paymentId(generateUuid())
                .amount(validPaymentRequest.amount())
                .currency(validPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private PaymentRequest getValidPaymentRequest() {
        return new PaymentRequest(new BigDecimal("123.45"), "PLN");
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}