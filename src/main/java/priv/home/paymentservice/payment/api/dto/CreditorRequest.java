package priv.home.paymentservice.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
public record CreditorRequest(
        @JsonProperty("name")
        @NotNull(message = "Creditor name is required")
        String creditorName,

        @JsonProperty("iban")
        @NotNull(message = "CreditorIBAN is required")
        @Pattern(regexp = "^[A-Z]{2,2}[0-9]{2,2}[a-zA-Z0-9]{1,30}$",
                message = "IBAN number must match pattern of IBAN2007Identifier")
        String creditorIban
) {
}
