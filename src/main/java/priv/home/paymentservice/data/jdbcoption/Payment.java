package priv.home.paymentservice.data.jdbcoption;

import lombok.Builder;
import lombok.Getter;
import priv.home.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;


@Builder
@Getter
// used previously by PaymentStorageJdbc
public class Payment {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;

}
