package priv.home.paymentservice.outbox.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ConditionalOnProperty(
        prefix = "payment-service.outbox.publisher",
        name = "publishing-mode",
        havingValue = "CONCURRENT"
)
public class OutboxPublishingWorkerConfig {


    @Bean(destroyMethod = "shutdown")
    public ExecutorService outboxPublishingExecutor(OutboxEventPublisherProperties publisherProperties) {
        return Executors.newFixedThreadPool(
                publisherProperties.publishingWorkerCount(),
                Thread.ofPlatform()
                        .name("outbox-publisher-", 0)
                        .factory());
    }
}
