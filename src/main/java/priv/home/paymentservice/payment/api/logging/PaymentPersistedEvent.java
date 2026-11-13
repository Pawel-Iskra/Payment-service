package priv.home.paymentservice.payment.api.logging;

import java.util.UUID;

public record PaymentPersistedEvent(
        UUID paymentId,
        UUID eventId
) {
}
