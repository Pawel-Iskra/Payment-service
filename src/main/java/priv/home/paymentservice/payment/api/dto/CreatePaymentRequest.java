package priv.home.paymentservice.payment.api.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CreatePaymentRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        @Digits(integer = 10,
                fraction = 2,
                message = "Amount must not exceed 9999999999.99 and must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$",
                message = "Currency code must consist of exactly 3 uppercase letters")
        String currency
) {
}
// TODO: unit tests to check all possible mismatches