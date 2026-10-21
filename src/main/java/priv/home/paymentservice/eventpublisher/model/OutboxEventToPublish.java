package priv.home.paymentservice.eventpublisher.model;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OutboxEventToPublish(UUID eventId, UUID paymentId, String eventType, String payload) {
}
