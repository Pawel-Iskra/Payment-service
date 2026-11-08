package priv.home.paymentservice.e2e;

import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(E2ETestKafkaContainerConfig.class)
public class AbstractE2EProcessingTest extends AbstractE2ETest {

    @DynamicPropertySource
    static void configurePostgresContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.listener.auto-startup", () -> true);
        registry.add("payment-service.payment-event-publisher.scheduler.publish", () -> "*/3 * * * * *");
        registry.add("payment-service.payment-event-publisher.scheduler.enabled", () -> true);
    }
}
