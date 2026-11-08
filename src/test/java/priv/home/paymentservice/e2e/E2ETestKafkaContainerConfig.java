package priv.home.paymentservice.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

@TestConfiguration(proxyBeanMethods = false)
public class E2ETestKafkaContainerConfig {

    private static final String KAFKA_DOCKER_IMAGE = "apache/kafka:4.1.1";

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_DOCKER_IMAGE);
    }
}