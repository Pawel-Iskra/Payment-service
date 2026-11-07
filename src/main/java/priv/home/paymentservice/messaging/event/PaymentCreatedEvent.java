package priv.home.paymentservice.messaging.event;

import lombok.Builder;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentCreatedEvent(
        UUID paymentId,
        UUID eventId,
        BigDecimal amount,
        String currency,
        PaymentStatus paymentStatus,
        String idempotencyKey) {
}
