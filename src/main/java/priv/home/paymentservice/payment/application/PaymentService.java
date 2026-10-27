package priv.home.paymentservice.payment.application;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.payment.model.PaymentStatus;
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
    public CreatePaymentResult createPayment(String idempotencyKey, CreatePaymentRequest createPaymentRequest) {
        Optional<PaymentEntity> paymentFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
        if (paymentFromDbOptional.isPresent()) {
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDbOptional.get());
        }
        PaymentEntity paymentToInsert = buildPaymentFromDto(idempotencyKey, createPaymentRequest);
        Optional<PaymentEntity> insertedPaymentOptional = paymentStorageJpa.insertPaymentIfAbsent(paymentToInsert);
        if (insertedPaymentOptional.isEmpty()) {
            PaymentEntity paymentFromDb = retrieveSinglePaymentByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Payment not found after idempotency conflict"));
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDb);
        }
        PaymentEntity paymentInserted = insertedPaymentOptional.get();
        UUID eventId = generateUuid();
        PaymentCreatedEvent paymentCreatedEvent = getPaymentCreatedEventFromPayment(
                eventId, idempotencyKey, paymentInserted);
        outboxStorageJpa.save(
                OutboxEventEntity.builder()
                        .eventId(eventId)
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


    private CreatePaymentResult buildPaymentAlreadyExistPaymentCreationDto(PaymentEntity payment) {
        return CreatePaymentResult.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private CreatePaymentResult buildPaymentSuccessfulPersistedPaymentCreationDto(PaymentEntity payment) {
        return CreatePaymentResult.builder()
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

    private PaymentEntity buildPaymentFromDto(String idempotencyKey, CreatePaymentRequest createPaymentRequest) {
        return PaymentEntity.builder()
                .paymentId(generateUuid())
                .amount(createPaymentRequest.amount())
                .currency(createPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(UUID eventId, String idempotencyKey, PaymentEntity paymentToPublish) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentToPublish.getPaymentId())
                .eventId(eventId)
                .amount(paymentToPublish.getAmount())
                .currency(paymentToPublish.getCurrency())
                .paymentStatus(paymentToPublish.getPaymentStatus())
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private String generatePayload(PaymentCreatedEvent paymentCreatedEvent) {
        return objectMapper.writeValueAsString(paymentCreatedEvent); // TODO: possible error handling?
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}
