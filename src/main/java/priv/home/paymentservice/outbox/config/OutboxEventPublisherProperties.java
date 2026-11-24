package priv.home.paymentservice.outbox.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "payment-service.outbox.publisher")
public record OutboxEventPublisherProperties(
        @Min(1) int batchSize,
        @Min(1) @DefaultValue("3")
        int publishingWorkerCount,
        @DefaultValue("SINGLE")
        OutboxPublishingMode publishingMode,
        @DefaultValue("PT1M")
        Duration leaseDuration,
        @NotNull @Valid Retry retry) {


    public OutboxEventPublisherProperties {
        if (leaseDuration == null || !leaseDuration.isPositive()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

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
