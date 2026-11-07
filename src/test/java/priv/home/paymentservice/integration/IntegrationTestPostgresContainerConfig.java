package priv.home.paymentservice.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestPostgresContainerConfig {

    private static final String POSTGRES_DOCKER_IMAGE = "postgres:18";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_DOCKER_IMAGE)
                .withDatabaseName("payment-service")
                .withUsername("test")
                .withPassword("test");
    }
}