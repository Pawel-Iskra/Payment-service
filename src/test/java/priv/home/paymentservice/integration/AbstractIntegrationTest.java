package priv.home.paymentservice.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("local")
@SpringBootTest
@Import(IntegrationTestPostgresContainerConfig.class)
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> false);
        registry.add("payment-service.payment-event-publisher.scheduler.enabled", () -> false);
        registry.add("payment-service.outbox.publisher.batch-size", () -> 3);
        registry.add("payment-service.outbox.publisher.retry.max-attempt", () -> 7);
    }
}
