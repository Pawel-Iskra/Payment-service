package priv.home.paymentservice.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Counter paymentsCreated;
    private final Counter paymentEventPublished;
    private final Counter paymentEventPublishAttemptFailed;


    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentsCreated = Counter.builder("paymentsCreated")
                .description("Total number of successfully created payments")
                .register(meterRegistry);
        this.paymentEventPublished = Counter.builder("paymentEventPublished")
                .description("Total number of published events to Kafka")
                .register(meterRegistry);
        this.paymentEventPublishAttemptFailed = Counter.builder("paymentEventPublishAttemptFailed")
                .description("Total number of failed payment event publishing attempts")
                .register(meterRegistry);
    }

    public void incrementPaymentsCreated() {
        paymentsCreated.increment();
    }

    public void incrementPaymentEventPublished() {
        paymentEventPublished.increment();
    }

    public void incrementFailedPublishAttempt() {
        paymentEventPublishAttemptFailed.increment();
    }
}
