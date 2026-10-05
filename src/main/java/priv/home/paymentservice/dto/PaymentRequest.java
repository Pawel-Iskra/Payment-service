package priv.home.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@NotNull
public record PaymentRequest(@Positive BigDecimal amount, @NotBlank String currency) {
}
