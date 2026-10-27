package priv.home.paymentservice.payment.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.application.PaymentService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";


    @Mock
    private PaymentService paymentService;
    @InjectMocks
    private PaymentController underTest;


    @Test
    public void whenPaymentAddedSuccessfullyThenShouldBeProperResponse() {
        // given
        CreatePaymentRequest properCreatePaymentRequest = getProperPaymentRequest();
        CreatePaymentResult createPaymentResult = getSuccessfulPaymentCreationResponse(properCreatePaymentRequest);
        Mockito.when(paymentService.createPayment(any(), any())).thenReturn(createPaymentResult);

        // when
        ResponseEntity<PaymentResponse> result = underTest.createPayment(IDEMPOTENCY_KEY, properCreatePaymentRequest);

        // then
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isNotNull();
    }

    @Test
    public void shouldReturnHttpOKForExistingPayment() {
        // given
        CreatePaymentRequest properCreatePaymentRequest = getProperPaymentRequest();
        PaymentResponse paymentResponse = getSuccessfullPaymentResponse(properCreatePaymentRequest);
        Mockito.when(paymentService.retrieveSinglePaymentByPaymentId(any())).thenReturn(Optional.of(paymentResponse));

        // when
        ResponseEntity<PaymentResponse> result = underTest.retrievePayment(paymentResponse.getPaymentId());

        // then
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }


    @Test
    public void shouldThrowExceptionForNotExistingPayment() {
        // given
        Mockito.when(paymentService.retrieveSinglePaymentByPaymentId(any())).thenReturn(Optional.empty());

        // when + then
        SinglePaymentNotFoundException exceptionThrown = assertThrows(
                SinglePaymentNotFoundException.class, () -> underTest.retrievePayment(generateUuid()));
        Assertions.assertThat(exceptionThrown.getMessage()).contains("Not found payment with given id");
    }


    private CreatePaymentRequest getProperPaymentRequest() {
        return new CreatePaymentRequest(new BigDecimal("123.45"), "PLN");
    }


    private CreatePaymentResult getSuccessfulPaymentCreationResponse(CreatePaymentRequest createPaymentRequest) {
        return CreatePaymentResult.builder()
                .paymentId(generateUuid())
                .amount(createPaymentRequest.amount())
                .currency(createPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private PaymentResponse getSuccessfullPaymentResponse(CreatePaymentRequest createPaymentRequest) {
        return PaymentResponse.builder()
                .paymentId(generateUuid())
                .amount(createPaymentRequest.amount())
                .currency(createPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}