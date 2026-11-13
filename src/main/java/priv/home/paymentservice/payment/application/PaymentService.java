package priv.home.paymentservice.payment.application;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import priv.home.paymentservice.messaging.event.PaymentCreatedEvent;
import priv.home.paymentservice.messaging.event.PaymentEventType;
import priv.home.paymentservice.outbox.persistence.OutboxEventEntity;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.logging.PaymentPersistedEvent;
import priv.home.paymentservice.payment.api.logging.PaymentPersistedEventListener;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {

    private final PaymentStorageJpa paymentStorageJpa;
    private final OutboxStorageJpa outboxStorageJpa;
    private final ObjectMapper objectMapper;
    private final PaymentPersistedEventListener persistedEventListener;


    @Transactional
    public CreatePaymentResult createPayment(UUID correlationId, String idempotencyKey, CreatePaymentRequest createPaymentRequest) {
        Optional<PaymentEntity> paymentFromDbOptional = retrieveSinglePaymentByIdempotencyKey(idempotencyKey);
        if (paymentFromDbOptional.isPresent()) {
            return buildPaymentAlreadyExistPaymentCreationDto(paymentFromDbOptional.get());
        }
        PaymentEntity paymentToInsert = buildPaymentFromRequest(idempotencyKey, createPaymentRequest);
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
                        .correlationId(correlationId)
                        .eventType(PaymentEventType.PAYMENT_CREATED_EVENT.name())
                        .payload(generatePayload(paymentCreatedEvent))
                        .createdAt(OffsetDateTime.now())
                        .build());
        persistedEventListener.handleLogging(new PaymentPersistedEvent(paymentInserted.getPaymentId(), eventId));
        return buildPaymentSuccessfulPersistedPaymentCreationDto(paymentInserted);
    }

    public Optional<PaymentRetrieveResult> retrieveSinglePaymentByPaymentId(UUID paymentId) {
        Optional<PaymentEntity> paymentOptional = paymentStorageJpa.findById(paymentId);
        return paymentOptional.map(this::buildPaymentRetrieveResponse);
    }


    private Optional<PaymentEntity> retrieveSinglePaymentByIdempotencyKey(String idempotencyKey) {
        return paymentStorageJpa.findByIdempotencyKey(idempotencyKey);
    }

    private CreatePaymentResult buildPaymentAlreadyExistPaymentCreationDto(PaymentEntity paymentEntity) {
        return CreatePaymentResult.builder()
                .paymentId(paymentEntity.getPaymentId())
                .endToEndId(paymentEntity.getEndToEndId())
                .debtorIban(paymentEntity.getDebtorIban())
                .creditorIban(paymentEntity.getCreditorIban())
                .amount(paymentEntity.getAmount())
                .currency(paymentEntity.getCurrency())
                .paymentStatus(paymentEntity.getPaymentStatus())
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private CreatePaymentResult buildPaymentSuccessfulPersistedPaymentCreationDto(PaymentEntity paymentEntity) {
        return CreatePaymentResult.builder()
                .paymentId(paymentEntity.getPaymentId())
                .endToEndId(paymentEntity.getEndToEndId())
                .debtorIban(paymentEntity.getDebtorIban())
                .creditorIban(paymentEntity.getCreditorIban())
                .amount(paymentEntity.getAmount())
                .currency(paymentEntity.getCurrency())
                .paymentStatus(paymentEntity.getPaymentStatus())
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private PaymentRetrieveResult buildPaymentRetrieveResponse(PaymentEntity paymentEntity) {
        return PaymentRetrieveResult.builder()
                .paymentId(paymentEntity.getPaymentId())
                .endToEndId(paymentEntity.getEndToEndId())
                .debtorIban(paymentEntity.getDebtorIban())
                .creditorIban(paymentEntity.getCreditorIban())
                .amount(paymentEntity.getAmount())
                .currency(paymentEntity.getCurrency())
                .paymentStatus(paymentEntity.getPaymentStatus())
                .build();
    }

    private PaymentEntity buildPaymentFromRequest(String idempotencyKey, CreatePaymentRequest createPaymentRequest) {
        return PaymentEntity.builder()
                .paymentId(generateUuid())
                .endToEndId(createPaymentRequest.endToEndId())
                .debtorIban(createPaymentRequest.debtorRequest().debtorIban())
                .creditorIban(createPaymentRequest.creditorRequest().creditorIban())
                .amount(createPaymentRequest.amount())
                .currency(createPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private PaymentCreatedEvent getPaymentCreatedEventFromPayment(UUID eventId, String idempotencyKey, PaymentEntity paymentEntity) {
        return PaymentCreatedEvent.builder()
                .paymentId(paymentEntity.getPaymentId())
                .eventId(eventId)
                .endToEndId(paymentEntity.getEndToEndId())
                .debtorIban(paymentEntity.getDebtorIban())
                .creditorIban(paymentEntity.getCreditorIban())
                .amount(paymentEntity.getAmount())
                .currency(paymentEntity.getCurrency())
                .paymentStatus(paymentEntity.getPaymentStatus())
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
