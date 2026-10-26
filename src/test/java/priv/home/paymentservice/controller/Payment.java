package priv.home.paymentservice.controller;

import lombok.Builder;
import lombok.Getter;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;


@Builder
@Getter
// used previously by PaymentStorageJdbc
// TODO: REMOVE AND REFACTOR where used
public class Payment {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;

}
