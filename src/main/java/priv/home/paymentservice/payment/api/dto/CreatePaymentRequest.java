package priv.home.paymentservice.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record CreatePaymentRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        @DecimalMax(value = "9999999999.99", message = "Amount must not exceed 9999999999.99")
        @Digits(integer = 100, fraction = 2,
                message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$",
                message = "Currency code must consist of exactly 3 uppercase letters")
        String currency,

        @JsonProperty("debtor")
        @Valid
        @NotNull(message = "Debtor is required")
        DebtorRequest debtorRequest,

        @JsonProperty("creditor")
        @Valid
        @NotNull(message = "Creditor is required")
        CreditorRequest creditorRequest,

        @NotBlank(message = "EndToEndId is required")
        @Size(min = 1, max = 35, message = "EndToEndId length must be between 1 and 35")
        String endToEndId
) {
}