package priv.home.paymentservice.outbox.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventPublisherPropertiesTest {

    private static final String PREFIX_PUBLISHER = "payment-service.outbox.publisher";
    private static final String PREFIX_PUBLISHER_RETRY = "payment-service.outbox.publisher.retry";
    private static final String BATCH_SIZE = PREFIX_PUBLISHER + ".batch-size";
    private static final String SCHEDULER_INTERVAL = PREFIX_PUBLISHER + ".scheduler-interval";
    private static final String MAX_ATTEMPT = PREFIX_PUBLISHER_RETRY + ".max-attempt";
    private static final String MULTIPLIER = PREFIX_PUBLISHER_RETRY + ".multiplier";
    private static final String INITIAL_BACKOFF = PREFIX_PUBLISHER_RETRY + ".initial-backoff";
    private static final String MAX_BACKOFF = PREFIX_PUBLISHER_RETRY + ".max-backoff";

    private static final String CONSTRUCTOR_THREW_EXCEPTION = "Constructor threw exception";
    // exception messages from OutboxEventPublisherProperties constructor
    private static final String INITIAL_BACKOFF_MUST_BE_POSITIVE = "initialBackoff must be positive";
    private static final String MAX_BACKOFF_MUST_BE_POSITIVE = "maxBackoff must be positive";
    private static final String MAX_BACKOFF_MUST_BE_GREATER_THAN_INITIAL_BACKOFF = "maxBackoff must be greater than initialBackoff";


    @Test
    public void shouldBindOutboxPublisherPropertiesSuccessfully() {
        // given
        MapConfigurationPropertySource propertiesSource = new MapConfigurationPropertySource(Map.of(
                BATCH_SIZE, "5",
                SCHEDULER_INTERVAL, "PT2M",
                MAX_ATTEMPT, "6",
                MULTIPLIER, "2",
                INITIAL_BACKOFF, "PT2M",
                MAX_BACKOFF, "PT1h"));

        // when
        BindResult<OutboxEventPublisherProperties> propertiesBindResult = new Binder(propertiesSource)
                .bind(PREFIX_PUBLISHER, Bindable.of(OutboxEventPublisherProperties.class));

        // then
        assertThat(propertiesBindResult.isBound()).isTrue();
        assertThat(propertiesBindResult.get()).satisfies(properties -> {
            assertThat(properties.batchSize()).isEqualTo(5);
            assertThat(properties.schedulerInterval()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.retry().maxAttempt()).isEqualTo(6);
            assertThat(properties.retry().multiplier()).isEqualTo(2);
            assertThat(properties.retry().initialBackoff()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.retry().maxBackoff()).isEqualTo(Duration.ofHours(1));
        });
    }

    @Test
    public void shouldThrowExceptionWhileBindingNotValidInitialBackoffValue() {
        // given
        MapConfigurationPropertySource propertiesSource = new MapConfigurationPropertySource(Map.of(
                BATCH_SIZE, "2",
                SCHEDULER_INTERVAL, "PT2M",
                MAX_ATTEMPT, "6",
                MULTIPLIER, "2",
                INITIAL_BACKOFF, "PT0M",
                MAX_BACKOFF, "PT1h"));

        // when + then
        assertThatThrownBy(() -> bindPropertiesSource(propertiesSource))
                .satisfies(thrown -> {
                    assertThat(thrown.getCause()).hasMessageContaining(CONSTRUCTOR_THREW_EXCEPTION);
                    assertThat(thrown).hasRootCauseMessage(INITIAL_BACKOFF_MUST_BE_POSITIVE);
                });
    }

    @Test
    public void shouldThrowExceptionWhileBindingNotValidMaxBackoffValue() {
        // given
        MapConfigurationPropertySource propertiesSource = new MapConfigurationPropertySource(Map.of(
                BATCH_SIZE, "2",
                SCHEDULER_INTERVAL, "PT2M",
                MAX_ATTEMPT, "6",
                MULTIPLIER, "2",
                INITIAL_BACKOFF, "PT2M",
                MAX_BACKOFF, "PT0h"));

        // when + then
        assertThatThrownBy(() -> bindPropertiesSource(propertiesSource))
                .satisfies(thrown -> {
                    assertThat(thrown.getCause()).hasMessageContaining(CONSTRUCTOR_THREW_EXCEPTION);
                    assertThat(thrown).hasRootCauseMessage(MAX_BACKOFF_MUST_BE_POSITIVE);
                });
    }

    @Test
    public void shouldThrowExceptionWhileBindingNotValidBackoffValues() {
        // given
        MapConfigurationPropertySource propertiesSource = new MapConfigurationPropertySource(Map.of(
                BATCH_SIZE, "2",
                SCHEDULER_INTERVAL, "PT2M",
                MAX_ATTEMPT, "6",
                MULTIPLIER, "2",
                INITIAL_BACKOFF, "PT12M",
                MAX_BACKOFF, "PT4M"));

        // when + then
        assertThatThrownBy(() -> bindPropertiesSource(propertiesSource))
                .satisfies(thrown -> {
                    assertThat(thrown.getCause()).hasMessageContaining(CONSTRUCTOR_THREW_EXCEPTION);
                    assertThat(thrown).hasRootCauseMessage(MAX_BACKOFF_MUST_BE_GREATER_THAN_INITIAL_BACKOFF);
                });
    }


    private void bindPropertiesSource(MapConfigurationPropertySource propertiesSource) {
        new Binder(propertiesSource).bind(PREFIX_PUBLISHER, Bindable.of(OutboxEventPublisherProperties.class));
    }
}

