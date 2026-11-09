package priv.home.paymentservice.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record DebtorRequest(
        @JsonProperty("name")
        @NotNull(message = "Debtor name is required")
        @Size(min = 1, max = 140, message = "Name length must be between 1 and 140")
        String debtorName,

        @JsonProperty("iban")
        @NotNull(message = "DebtorIBAN is required")
        @Pattern(regexp = "^[A-Z]{2,2}[0-9]{2,2}[a-zA-Z0-9]{1,30}$",
                message = "IBAN number must match pattern of IBAN2007Identifier")
        String debtorIban
) {
}
