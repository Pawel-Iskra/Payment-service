package priv.home.paymentservice.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import priv.home.paymentservice.eventconsumer.persistence.ConsumedEventStorageJpa;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles(profiles = {"local"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureTestRestTemplate
public abstract class AbstractE2ETest {

    private static final String POSTGRES_DOCKER_IMAGE = "postgres:18";
    private static final String KAFKA_DOCKER_IMAGE = "apache/kafka:4.1.1";

    @Autowired
    protected TestRestTemplate testRestTemplate;
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected PaymentStorageJpa paymentStorageJpa;
    @Autowired
    protected OutboxStorageJpa outboxStorageJpa;
    @Autowired
    protected ConsumedEventStorageJpa consumedEventStorageJpa;
    @Autowired
    protected OutboxEventPublisherProperties publisherProperties;


    @LocalServerPort
    protected int port;

    @Container
    protected static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(KAFKA_DOCKER_IMAGE);

    @Container
    protected static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(
            POSTGRES_DOCKER_IMAGE)
            .withDatabaseName("payment-service")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configurePostgresContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);

        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);

        registry.add("payment-service.payment-event-publisher.scheduler.publish", () -> "*/3 * * * * *");
    }

    @BeforeEach
    protected void cleanTables() {
        jdbcTemplate.update("DELETE FROM consumed_event");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM payment");
    }

    // TODO: possible extension KafkaTestConsumer
}
