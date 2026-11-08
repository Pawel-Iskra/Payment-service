package priv.home.paymentservice.e2e;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class E2ETestPostgresContainerConfig {

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