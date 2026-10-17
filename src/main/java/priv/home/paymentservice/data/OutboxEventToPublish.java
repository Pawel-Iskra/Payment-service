package priv.home.paymentservice.data;

import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;

import java.util.UUID;

public record OutboxEventToPublish(UUID eventId, PaymentCreatedEvent paymentCreatedEvent) {
}
