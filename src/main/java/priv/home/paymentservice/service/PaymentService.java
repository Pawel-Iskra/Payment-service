package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorage;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {


    private final PaymentStorage paymentStorage;


    public PaymentResponse createPayment(PaymentRequest paymentRequest) {
        Payment payment = buildPaymentFromDto(paymentRequest);
        paymentStorage.addPaymentToStorage(payment);
        return buildPaymentSuccessfulResponse(payment);
    }


    private PaymentResponse buildPaymentSuccessfulResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
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
