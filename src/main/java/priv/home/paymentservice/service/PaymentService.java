package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {


    private final PaymentStorageJdbc paymentStorageJdbc;


    public PaymentResponse createPayment(PaymentRequest paymentRequest) {
        Payment payment = buildPaymentFromDto(paymentRequest);
        paymentStorageJdbc.addPaymentToStorage(payment);
        return buildPaymentSuccessfulResponse(payment);
    }

    public Optional<PaymentResponse> retrieveSinglePaymentByPaymentId(UUID paymentId) {
        Optional<Payment> paymentOptional = paymentStorageJdbc.retrievePaymentByPaymentId(paymentId);
        return paymentOptional.map(this::buildPaymentSuccessfulResponse);
    }


    private PaymentResponse buildPaymentSuccessfulResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }


    private Payment buildPaymentFromDto(PaymentRequest paymentRequest) {
        return Payment.builder()
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
