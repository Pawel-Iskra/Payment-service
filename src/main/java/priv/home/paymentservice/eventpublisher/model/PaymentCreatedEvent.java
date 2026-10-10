package priv.home.paymentservice.eventpublisher.model;

import lombok.Builder;
import lombok.Getter;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
public class PaymentCreatedEvent {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final String idempotencyKey;
}
