package priv.home.paymentservice.eventpublisher;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventPublisherPropertiesValidationTest {

    private static final String PREFIX_PUBLISHER = "payment-service.outbox.publisher.";
    private static final String PREFIX_PUBLISHER_RETRY = "payment-service.outbox.publisher.retry.";
    private static final String BATCH_SIZE = "batch-size";
    private static final String SCHEDULER_INTERVAL = "scheduler-interval";
    private static final String MAX_ATTEMPT = "max-attempt";
    private static final String MULTIPLIER = "multiplier";
    private static final String INITIAL_BACKOFF = "initial-backoff";
    private static final String MAX_BACKOFF = "max-backoff";
    private static final String REJECTED_VALUE_ZERO = "rejected value [0]";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestContextConfiguration.class);


    @Test
    public void shouldCreateBeanAndStartSuccessfully() {
        contextRunner.withPropertyValues(
                        PREFIX_PUBLISHER + BATCH_SIZE + "=5",
                        PREFIX_PUBLISHER + SCHEDULER_INTERVAL + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_ATTEMPT + "=6",
                        PREFIX_PUBLISHER_RETRY + MULTIPLIER + "=2",
                        PREFIX_PUBLISHER_RETRY + INITIAL_BACKOFF + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_BACKOFF + "=PT1h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OutboxEventPublisherProperties.class);
                });
    }

    @Test
    public void shouldRejectZeroBatchSize() {
        contextRunner.withPropertyValues(
                        PREFIX_PUBLISHER + BATCH_SIZE + "=0",
                        PREFIX_PUBLISHER + SCHEDULER_INTERVAL + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_ATTEMPT + "=6",
                        PREFIX_PUBLISHER_RETRY + MULTIPLIER + "=2",
                        PREFIX_PUBLISHER_RETRY + INITIAL_BACKOFF + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_BACKOFF + "=PT1h")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(REJECTED_VALUE_ZERO);
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("batchSize");
                });
    }

    @Test
    public void shouldRejectZeroMaxAttempt() {
        contextRunner.withPropertyValues(
                        PREFIX_PUBLISHER + BATCH_SIZE + "=5",
                        PREFIX_PUBLISHER + SCHEDULER_INTERVAL + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_ATTEMPT + "=0",
                        PREFIX_PUBLISHER_RETRY + MULTIPLIER + "=2",
                        PREFIX_PUBLISHER_RETRY + INITIAL_BACKOFF + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_BACKOFF + "=PT1h")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(REJECTED_VALUE_ZERO);
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("maxAttempt");
                });
    }

    @Test
    public void shouldRejectZeroMultiplier() {
        contextRunner.withPropertyValues(
                        PREFIX_PUBLISHER + BATCH_SIZE + "=5",
                        PREFIX_PUBLISHER + SCHEDULER_INTERVAL + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_ATTEMPT + "=6",
                        PREFIX_PUBLISHER_RETRY + MULTIPLIER + "=0",
                        PREFIX_PUBLISHER_RETRY + INITIAL_BACKOFF + "=PT2M",
                        PREFIX_PUBLISHER_RETRY + MAX_BACKOFF + "=PT1h")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(REJECTED_VALUE_ZERO);
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("multiplier");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OutboxEventPublisherProperties.class)
    static class TestContextConfiguration {
    }
}