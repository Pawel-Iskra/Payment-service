package priv.home.paymentservice.payment.application;

import lombok.Builder;
import lombok.Getter;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
//TODO: record
public class CreatePaymentResult {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final boolean wasAlreadyInDb;
}
