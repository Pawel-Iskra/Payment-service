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
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;
import priv.home.paymentservice.service.PaymentService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {


    @Mock
    private PaymentService paymentService;
    @InjectMocks
    private PaymentController underTest;


    @Test
    public void whenPaymentAddedSuccessfullyThenShouldBeProperResponse() {
        // given
        PaymentRequest properPaymentRequest = getProperPaymentRequest();
        PaymentResponse paymentResponse = getSuccessfullPaymentResponse(properPaymentRequest);
        Mockito.when(paymentService.createPayment(any())).thenReturn(paymentResponse);

        // when
        ResponseEntity<PaymentResponse> result = underTest.createPayment(properPaymentRequest);

        // then
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isEqualTo(paymentResponse);
    }


    private PaymentRequest getProperPaymentRequest() {
        return new PaymentRequest(new BigDecimal("123.45"), "PLN");
    }

    private PaymentResponse getSuccessfullPaymentResponse(PaymentRequest paymentRequest) {
        return PaymentResponse.builder()
                .id(generateUuid())
                .amount(paymentRequest.amount())
                .currency(paymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}