package priv.home.paymentservice.payment.application;

import lombok.Builder;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CreatePaymentResult(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        PaymentStatus paymentStatus,
        boolean wasAlreadyInDb) {
}
