package priv.home.paymentservice.messaging.event;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.jackson.Jacksonized;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
@Jacksonized
@ToString
public class PaymentCreatedEvent {

    private final UUID paymentId;
    private final UUID eventId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final String idempotencyKey;
}
