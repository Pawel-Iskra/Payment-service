package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.data.OutboxStorageJdbc;
import priv.home.paymentservice.data.PaymentStorageJdbc;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentEventType;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {

    private final PaymentStorageJdbc paymentStorageJdbc;
    private final OutboxStorageJdbc outboxStorageJdbc;
    private final ObjectMapper objectMapper;


    @Transactional
    public PaymentCreationDto createPayment(String idempotencyKey, PaymentRequest paymentRequest) {
        Optional<Payment> paymentFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
        if (paymentFromDbOptional.isPresent()) {
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDbOptional.get());
        }
        Payment paymentToInsert = buildPaymentFromDto(paymentRequest);
        Optional<Payment> insertedPaymentOptional = paymentStorageJdbc.insertPaymentIfAbsent(idempotencyKey, paymentToInsert);
        if (insertedPaymentOptional.isEmpty()) {
            Payment paymentFromDb = paymentStorageJdbc.retrievePaymentByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Payment not found after idempotency conflict"));
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDb);
        }
        Payment paymentInserted = insertedPaymentOptional.get();
        PaymentCreatedEvent paymentCreatedEvent = getPaymentCreatedEventFromPayment(idempotencyKey, paymentInserted);
        outboxStorageJdbc.addPaymentEventToOutbox(
                generateUuid(),
                paymentInserted.getPaymentId(),
                PaymentEventType.PAYMENT_CREATED_EVENT.name(),
                generatePayload(paymentCreatedEvent));
        return buildPaymentSuccessfulPersistedPaymentCreationDto(paymentInserted);
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

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(String idempotencyKey, Payment paymentToPublish) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentToPublish.getPaymentId())
                .amount(paymentToPublish.getAmount())
                .currency(paymentToPublish.getCurrency())
                .paymentStatus(paymentToPublish.getPaymentStatus())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private String generatePayload(PaymentCreatedEvent paymentCreatedEvent) {
        return objectMapper.writeValueAsString(paymentCreatedEvent);
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}
