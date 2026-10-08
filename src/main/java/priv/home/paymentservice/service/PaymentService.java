package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {


    private final PaymentStorageJdbc paymentStorageJdbc;


    public PaymentCreationDto createPayment(String idempotencyKey, PaymentRequest paymentRequest) {
        Optional<Payment> resultFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
        if (resultFromDbOptional.isPresent()) {
            return buildPaymentAlreadyExistPaymentCreationResponse(resultFromDbOptional.get());
        }
        Payment payment = buildPaymentFromDto(paymentRequest);
        paymentStorageJdbc.addPaymentToStorage(idempotencyKey, payment);
        return buildPaymentSuccessfulPaymentCreationResponse(payment);
    }

    public Optional<PaymentResponse> retrieveSinglePaymentByPaymentId(UUID paymentId) {
        Optional<Payment> paymentOptional = paymentStorageJdbc.retrievePaymentByPaymentId(paymentId);
        return paymentOptional.map(this::buildPaymentSuccessfulResponse);
    }

    public Optional<Payment> retrieveSinglePaymentByIdempotencyKey(String idempotencyKey) {
        return paymentStorageJdbc.retrievePaymentByIdempotencyKey(idempotencyKey);
    }


    private PaymentCreationDto buildPaymentAlreadyExistPaymentCreationResponse(Payment payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private PaymentCreationDto buildPaymentSuccessfulPaymentCreationResponse(Payment payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private PaymentResponse buildPaymentSuccessfulResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
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
