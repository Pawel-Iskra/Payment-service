package priv.home.paymentservice.outbox.application;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;

import java.time.Duration;

@Component
@AllArgsConstructor
public class OutboxRetryPolicy {

    private final OutboxEventPublisherProperties publisherProperties;


    public Duration calculateBackoff(int attemptsDone) {
        OutboxEventPublisherProperties.Retry retryProperties = publisherProperties.retry();
        int multiplier = Math.powExact(retryProperties.multiplier(), attemptsDone);
        Duration calculatedBackoff = retryProperties.initialBackoff().multipliedBy(multiplier);
        return calculatedBackoff.compareTo(retryProperties.maxBackoff()) > 0 ?
                retryProperties.maxBackoff() : calculatedBackoff;
    }
}
