package priv.home.paymentservice.dto;

import lombok.Builder;
import lombok.Getter;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
public class PaymentCreationDto {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final boolean wasAlreadyInDb;
}
