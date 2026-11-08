package priv.home.paymentservice.payment.api.dto;


import lombok.Builder;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record PaymentResponse(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        PaymentStatus paymentStatus) {
}
