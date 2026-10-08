package priv.home.paymentservice.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
public class PaymentCreationResponse {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final boolean wasAlreadyInDb;
}
