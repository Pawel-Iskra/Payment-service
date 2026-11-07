package priv.home.paymentservice.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import priv.home.paymentservice.eventconsumer.persistence.ConsumedEventStorageJpa;
import priv.home.paymentservice.outbox.config.OutboxEventPublisherProperties;
import priv.home.paymentservice.outbox.persistence.OutboxStorageJpa;
import priv.home.paymentservice.payment.persistence.PaymentStorageJpa;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles(profiles = {"local"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=false",
        "payment-service.payment-event-publisher.scheduler.enabled=false"
})
@Import(E2ETestPostgresContainerConfig.class)
public abstract class AbstractE2ETest {

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

    @BeforeEach
    protected void cleanTables() {
        jdbcTemplate.update("DELETE FROM consumed_event");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM payment");
    }
}
