package priv.home.paymentservice.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import priv.home.paymentservice.data.PaymentStorage;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;


@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {


    private static final String PAYMENT_NOT_VALID = "Given payment request is not valid (the amount is not positive or currency field is empty).";
    private static final String PAYMENT_SUCCESSFULLY_ADDED_TO_STORAGE = "Payment was successfully added to the payment storage";

    @Mock
    private PaymentStorage paymentStorage;
    @InjectMocks
    private PaymentService underTest;


    @Test
    public void checkIfValuesInPaymentDomainAreEqualToValuesInPaymentRequest() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getAmount()).isEqualTo(paymentRequest.amount());
        Assertions.assertThat(paymentResponseResult.getCurrency()).isEqualTo(paymentRequest.currency());
    }

    @Test
    public void isUuidGeneratedWhenPaymentIsCreated() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getId()).isNotNull();
    }

    @Test
    public void isStatusCreatedAndSuitableMessageWhenPaymentIsCreated() {
        // given
        PaymentRequest paymentRequest = getValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getPaymentStatus()).isEqualTo(PaymentStatus.CREATED);
        Assertions.assertThat(paymentResponseResult.getResponseMessage()).isEqualTo(PAYMENT_SUCCESSFULLY_ADDED_TO_STORAGE);
    }

    @Test
    public void whenRequestInvalidThenStatusNotCreatedAndSuitableMessage() {
        // given
        PaymentRequest paymentRequest = getNotValidPaymentRequest();

        // when
        PaymentResponse paymentResponseResult = underTest.createPayment(paymentRequest);

        // then
        Assertions.assertThat(paymentResponseResult.getPaymentStatus()).isNull();
        Assertions.assertThat(paymentResponseResult.getResponseMessage()).isEqualTo(PAYMENT_NOT_VALID);
    }


    private PaymentRequest getValidPaymentRequest() {
        return new PaymentRequest(new BigDecimal("123.45"), "PLN");
    }

    private PaymentRequest getNotValidPaymentRequest() {
        return new PaymentRequest(new BigDecimal("0"), "PLN");
    }
}