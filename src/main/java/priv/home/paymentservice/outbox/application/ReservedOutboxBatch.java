package priv.home.paymentservice.outbox.application;

import priv.home.paymentservice.outbox.model.OutboxEventToPublish;

import java.util.List;
import java.util.UUID;

public record ReservedOutboxBatch(
        UUID claimToken,
        List<OutboxEventToPublish> eventToPublish
) {
}
