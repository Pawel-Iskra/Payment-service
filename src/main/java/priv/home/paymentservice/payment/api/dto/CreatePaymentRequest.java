package priv.home.paymentservice.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;

public record CreatePaymentRequest(@NotNull @Positive BigDecimal amount, @NotBlank @Length(max = 3) String currency) {
}
