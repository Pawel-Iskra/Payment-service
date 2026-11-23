package priv.home.paymentservice.outbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
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
