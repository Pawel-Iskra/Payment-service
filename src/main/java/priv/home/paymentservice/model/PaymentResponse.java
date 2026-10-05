package priv.home.paymentservice.model;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
@Getter
public class PaymentResponse {

    private final UUID id;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final boolean isSuccessfullyAddedToPaymentStorage;
    private final String responseMessage;

}
