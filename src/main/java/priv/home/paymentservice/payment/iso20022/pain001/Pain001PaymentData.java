package priv.home.paymentservice.payment.iso20022.pain001;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Builder
public record Pain001PaymentData(
        String messageId,
        OffsetDateTime creationDateTime,
        int transactionCount,
        String paymentInformationId,
        String endToEndId,
        BigDecimal amount,
        String currency,
        String debtorName,
        String debtorIban,
        String debtorBic,
        String creditorName,
        String creditorIban,
        String creditorBic) {
}
