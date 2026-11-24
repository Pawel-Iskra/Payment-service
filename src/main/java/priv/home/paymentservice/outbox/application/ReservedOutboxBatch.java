package priv.home.paymentservice.outbox.application;

import lombok.Builder;
import priv.home.paymentservice.outbox.model.OutboxEventToPublish;

import java.util.List;
import java.util.UUID;

@Builder
public record ReservedOutboxBatch(
        UUID claimToken,
        List<OutboxEventToPublish> eventsToPublish
) {
}
