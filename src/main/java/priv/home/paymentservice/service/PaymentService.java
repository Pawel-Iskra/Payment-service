package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.data.OutboxStorageJpa;
import priv.home.paymentservice.data.PaymentStorageJpa;
import priv.home.paymentservice.dto.PaymentCreationDto;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import priv.home.paymentservice.model.*;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {

    private final PaymentStorageJpa paymentStorageJpa;
    private final OutboxStorageJpa outboxStorageJpa;
    private final ObjectMapper objectMapper;


    @Transactional
    public PaymentCreationDto createPayment(String idempotencyKey, PaymentRequest paymentRequest) {
        Optional<PaymentEntity> paymentFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
        if (paymentFromDbOptional.isPresent()) {
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDbOptional.get());
        }
        PaymentEntity paymentToInsert = buildPaymentFromDto(idempotencyKey, paymentRequest);
        Optional<PaymentEntity> insertedPaymentOptional = paymentStorageJpa.insertPaymentIfAbsent(paymentToInsert);
        if (insertedPaymentOptional.isEmpty()) {
            PaymentEntity paymentFromDb = retrieveSinglePaymentByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Payment not found after idempotency conflict"));
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDb);
        }
        PaymentEntity paymentInserted = insertedPaymentOptional.get();
        PaymentCreatedEvent paymentCreatedEvent = getPaymentCreatedEventFromPayment(idempotencyKey, paymentInserted);
        outboxStorageJpa.save(
                OutboxEventEntity.builder()
                        .eventId(generateUuid())
                        .paymentId(paymentInserted.getPaymentId())
                        .eventType(PaymentEventType.PAYMENT_CREATED_EVENT.name())
                        .payload(generatePayload(paymentCreatedEvent))
                        .createdAt(OffsetDateTime.now())
                        .build());
        return buildPaymentSuccessfulPersistedPaymentCreationDto(paymentInserted);
    }

    public Optional<PaymentResponse> retrieveSinglePaymentByPaymentId(UUID paymentId) {
        Optional<PaymentEntity> paymentOptional = paymentStorageJpa.findById(paymentId);
        return paymentOptional.map(this::buildPaymentSuccessfulResponse);
    }

    public Optional<PaymentEntity> retrieveSinglePaymentByIdempotencyKey(String idempotencyKey) {
        return paymentStorageJpa.findByIdempotencyKey(idempotencyKey);
    }


    private PaymentCreationDto buildPaymentAlreadyExistPaymentCreationDto(PaymentEntity payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private PaymentCreationDto buildPaymentSuccessfulPersistedPaymentCreationDto(PaymentEntity payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private PaymentResponse buildPaymentSuccessfulResponse(PaymentEntity payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }

    private PaymentEntity buildPaymentFromDto(String idempotencyKey, PaymentRequest paymentRequest) {
        return PaymentEntity.builder()
                .paymentId(generateUuid())
                .amount(paymentRequest.amount())
                .currency(paymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(String idempotencyKey, PaymentEntity paymentToPublish) {
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
