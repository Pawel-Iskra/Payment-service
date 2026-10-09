package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;
import priv.home.paymentservice.publisher.PaymentEventPublisher;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {


    private final PaymentStorageJdbc paymentStorageJdbc;
    private final PaymentEventPublisher eventPublisher;


    public PaymentCreationDto createPayment(String idempotencyKey, PaymentRequest paymentRequest) {
        Optional<Payment> paymentFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
        if (paymentFromDbOptional.isPresent()) {
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDbOptional.get());
        }
        Payment payment = buildPaymentFromDto(paymentRequest);
        try {
            paymentStorageJdbc.addPaymentToStorage(idempotencyKey, payment);
        } catch (DuplicateKeyException duplicateKeyException) {
            paymentFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
            if (paymentFromDbOptional.isPresent()) {
                return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDbOptional.get());
            }
            throw duplicateKeyException;
        }
        eventPublisher.publish(idempotencyKey, payment);
        return buildPaymentSuccessfulPersistedPaymentCreationDto(payment);
    }

    public Optional<PaymentResponse> retrieveSinglePaymentByPaymentId(UUID paymentId) {
        Optional<Payment> paymentOptional = paymentStorageJdbc.retrievePaymentByPaymentId(paymentId);
        return paymentOptional.map(this::buildPaymentSuccessfulResponse);
    }

    public Optional<Payment> retrieveSinglePaymentByIdempotencyKey(String idempotencyKey) {
        return paymentStorageJdbc.retrievePaymentByIdempotencyKey(idempotencyKey);
    }


    private PaymentCreationDto buildPaymentAlreadyExistPaymentCreationDto(Payment payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private PaymentCreationDto buildPaymentSuccessfulPersistedPaymentCreationDto(Payment payment) {
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
