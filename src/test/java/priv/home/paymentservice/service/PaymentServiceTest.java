package priv.home.paymentservice.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {


    @Mock
    private PaymentStorageJdbc paymentStorageJdbc;
    @InjectMocks
    private PaymentService underTest;


    @Test
    public void shouldAddPaymentToStorage() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        underTest.createPayment(paymentRequest);

        // then
        verify(paymentStorageJdbc).addPaymentToStorage(any(Payment.class));
    }

    @Test
    public void shouldAddPaymentToStorageWithCorrectValues() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();
        ArgumentCaptor<Payment> paymentArgumentCaptor = ArgumentCaptor.forClass(Payment.class);

        // when + then
        underTest.createPayment(paymentRequest);
        verify(paymentStorageJdbc).addPaymentToStorage(paymentArgumentCaptor.capture());
        Payment savedPayment = paymentArgumentCaptor.getValue();

        Assertions.assertThat(savedPayment.getAmount()).isEqualTo(paymentRequest.amount());
        Assertions.assertThat(savedPayment.getCurrency()).isEqualTo(paymentRequest.currency());
        Assertions.assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        Assertions.assertThat(savedPayment.getPaymentId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithRequestedAmountAndCurrency() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getAmount()).isEqualTo(paymentRequest.amount());
        Assertions.assertThat(paymentResponseResult.getCurrency()).isEqualTo(paymentRequest.currency());
    }

    @Test
    public void shouldGenerateIdForNewPayment() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getId()).isNotNull();
    }

    @Test
    public void shouldCreatePaymentWithCreatedStatus() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    public void shouldReturnExistingPaymentFromStorage() {
        // given
        PaymentRequest validPaymentRequest = getValidPaymentRequest();
        Payment validPayment = getValidPaymentFromRequest(validPaymentRequest);
        Mockito.when(paymentStorageJdbc.retrievePaymentByPaymentId(any())).thenReturn(Optional.of(validPayment));

        // when
        Optional<PaymentResponse> result = underTest.retrieveSinglePaymentByPaymentId(validPayment.getPaymentId());

        // then
        Assertions.assertThat(result).isNotEmpty();
        Assertions.assertThat(result.get().getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        Assertions.assertThat(result.get().getCurrency()).isEqualTo(validPayment.getCurrency());
        Assertions.assertThat(result.get().getAmount()).isEqualTo(validPayment.getAmount());
    }

    @Test
    public void shouldReturnEmptyOptionalForNotExistingPayment() {
        // given
        Mockito.when(paymentStorageJdbc.retrievePaymentByPaymentId(any())).thenReturn(Optional.empty());

        // when
        Optional<PaymentResponse> result = underTest.retrieveSinglePaymentByPaymentId(generateUuid());

        // then
        Assertions.assertThat(result).isEmpty();
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