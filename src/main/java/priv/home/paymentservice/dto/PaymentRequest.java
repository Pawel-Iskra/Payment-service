package priv.home.paymentservice.dto;

import java.math.BigDecimal;

public record PaymentRequest(BigDecimal amount, String currency) {
}
