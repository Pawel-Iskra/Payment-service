package priv.home.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public class PaymentDto {

    private final BigDecimal amount;
    private final String currency;
}
