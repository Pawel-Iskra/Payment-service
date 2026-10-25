package priv.home.paymentservice.eventpublisher;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "payment-service.outbox.publisher")
public record OutboxEventPublisherProperties(
        @Min(1) int batchSize,
        Duration schedulerInterval,
        @Valid Retry retry) {

    public record Retry(
            @Min(1) int maxAttempt,
            @Min(1) int multiplier,
            Duration initialBackoff,
            Duration maxBackoff) {

        public Retry {
            if (initialBackoff == null || !initialBackoff.isPositive()) {
                throw new IllegalArgumentException("initialBackoff must be positive");
            }
            if (maxBackoff == null || !maxBackoff.isPositive()) {
                throw new IllegalArgumentException("maxBackoff must be positive");
            }
            if (maxBackoff.compareTo(initialBackoff) <= 0) {
                throw new IllegalArgumentException("maxBackoff must be greater than initialBackoff");
            }
        }
    }
}
