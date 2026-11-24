package priv.home.paymentservice.outbox.model;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ReservedOutboxBatch(
        UUID claimToken,
        List<OutboxEventToPublish> eventsToPublish
) {
}
