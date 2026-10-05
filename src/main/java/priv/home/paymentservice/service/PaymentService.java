package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorage;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {


    private static final BigDecimal BIG_DECIMAL_ZERO = new BigDecimal(0);
    private static final String PAYMENT_NOT_VALID = "Given payment request is not valid (the amount is not positive or currency field is empty).";
    private static final String PAYMENT_SUCCESSFULLY_ADDED_TO_STORAGE = "Payment was successfully added to the payment storage";


    private final PaymentStorage paymentStorage;


    public PaymentResponse createPayment(PaymentRequest paymentRequest) {
        boolean isRequestValid = isPaymentRequestValid(paymentRequest);
        if (!isRequestValid) {
            return buildPaymentResponseValidationFailed(paymentRequest);
        }
        Payment payment = buildPaymentFromDto(paymentRequest);
        paymentStorage.addPaymentToStorage(payment);
        return buildPaymentSuccessfulResponse(payment);
    }


    private boolean isPaymentRequestValid(PaymentRequest paymentRequest) {
        return BIG_DECIMAL_ZERO.compareTo(paymentRequest.amount()) < 0 && !paymentRequest.currency().isBlank();
    }


    private PaymentResponse buildPaymentSuccessfulResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .isSuccessfullyAddedToPaymentStorage(Boolean.TRUE)
                .responseMessage(PAYMENT_SUCCESSFULLY_ADDED_TO_STORAGE)
                .build();
    }

    private PaymentResponse buildPaymentResponseValidationFailed(PaymentRequest paymentRequest) {
        return PaymentResponse.builder()
                .amount(paymentRequest.amount())
                .currency(paymentRequest.currency())
                .isSuccessfullyAddedToPaymentStorage(Boolean.FALSE)
                .responseMessage(PAYMENT_NOT_VALID)
                .build();
    }


    private Payment buildPaymentFromDto(PaymentRequest paymentRequest) {
        return Payment.builder()
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
