package priv.home.paymentservice.controller;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.exceptions.SinglePaymentNotFoundException;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;
import priv.home.paymentservice.service.PaymentService;

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
        PaymentRequest properPaymentRequest = getProperPaymentRequest();
        PaymentCreationDto paymentCreationDto = getSuccessfulPaymentCreationResponse(properPaymentRequest);
        Mockito.when(paymentService.createPayment(any(), any())).thenReturn(paymentCreationDto);

        // when
        ResponseEntity<PaymentResponse> result = underTest.createPayment(IDEMPOTENCY_KEY, properPaymentRequest);

        // then
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isNotNull();
    }

    @Test
    public void shouldReturnHttpOKForExistingPayment() {
        // given
        PaymentRequest properPaymentRequest = getProperPaymentRequest();
        PaymentResponse paymentResponse = getSuccessfullPaymentResponse(properPaymentRequest);
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


    private PaymentRequest getProperPaymentRequest() {
        return new PaymentRequest(new BigDecimal("123.45"), "PLN");
    }


    private PaymentCreationDto getSuccessfulPaymentCreationResponse(PaymentRequest paymentRequest) {
        return PaymentCreationDto.builder()
                .paymentId(generateUuid())
                .amount(paymentRequest.amount())
                .currency(paymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private PaymentResponse getSuccessfullPaymentResponse(PaymentRequest paymentRequest) {
        return PaymentResponse.builder()
                .paymentId(generateUuid())
                .amount(paymentRequest.amount())
                .currency(paymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}