package priv.home.paymentservice.observability.logging;

import java.util.UUID;

public record PaymentPersistedEvent(
        UUID paymentId,
        UUID eventId
) {
}
