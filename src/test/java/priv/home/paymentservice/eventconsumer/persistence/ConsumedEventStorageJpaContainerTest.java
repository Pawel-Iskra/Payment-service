package priv.home.paymentservice.eventconsumer.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Testcontainers
class ConsumedEventStorageJpaContainerTest {


    private static final String POSTGRES_DOCKER_IMAGE = "postgres:18";
    private static final String SELECT_ALL_FROM_CONSUMED_EVENT_TABLE = """
            SELECT event_id, consumed_at
            FROM consumed_event
            """;


    @Container
    private static final PostgreSQLContainer POSTGRES_CONTAINER = new PostgreSQLContainer(POSTGRES_DOCKER_IMAGE);

    @DynamicPropertySource
    static void configurePostgresContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ConsumedEventStorageJpa underTest;

    @BeforeEach
    public void cleanTable() {
        jdbcTemplate.update("DELETE FROM consumed_event");
    }


    @Test
    public void shouldAddRecordToDb() {
        // given
        UUID eventId = generateUuid();

        // when
        Optional<ConsumedEventEntity> resultEventOptional = underTest.insertConsumedEventIfAbsent(eventId);

        // then
        List<ConsumedEventEntity> recordsFromDb = retrieveAllRecordsFromConsumedEventTableWithJdbc();
        assertThat(recordsFromDb)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getEventId()).isEqualTo(eventId);
                    assertThat(record.getConsumedAt()).isNotNull();
                });
        assertThat(resultEventOptional)
                .hasValueSatisfying(resultEvent -> {
                    assertThat(resultEvent.getEventId()).isEqualTo(eventId);
                    assertThat(resultEvent.getConsumedAt()).isNotNull();
                });
    }

    @Test
    public void shouldNotAddRecordToDbAndReturnEmptyOptional() {
        // given
        UUID eventId = generateUuid();
        jdbcTemplate.update("INSERT INTO consumed_event (event_id) VALUES (?)", eventId);

        // when
        Optional<ConsumedEventEntity> resultEventOptional = underTest.insertConsumedEventIfAbsent(eventId);

        // then
        List<ConsumedEventEntity> recordsFromDb = retrieveAllRecordsFromConsumedEventTableWithJdbc();
        assertThat(recordsFromDb).isNotEmpty();
        assertThat(recordsFromDb.size()).isOne();

        assertThat(resultEventOptional).isEmpty();
    }

    @Test
    public void shouldAddOneRecordToDbWhenRaceCondition() throws ExecutionException, InterruptedException, TimeoutException {
        // given
        UUID eventId = generateUuid();
        int threads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Optional<ConsumedEventEntity>> insertEvent = () -> {
            readyLatch.countDown();
            startLatch.await();
            return underTest.insertConsumedEventIfAbsent(eventId);
        };

        // when + then
        try {
            Future<Optional<ConsumedEventEntity>> firstThread = executorService.submit(insertEvent);
            Future<Optional<ConsumedEventEntity>> secondThread = executorService.submit(insertEvent);

            assertThat(readyLatch.await(4, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            Optional<ConsumedEventEntity> firstThreadResult = firstThread.get(4, TimeUnit.SECONDS);
            Optional<ConsumedEventEntity> secondThreadResult = secondThread.get(4, TimeUnit.SECONDS);

            assertThat(firstThreadResult.isPresent()).isNotEqualTo(secondThreadResult.isPresent());
            ConsumedEventEntity resultEvent = firstThreadResult.orElseGet(secondThreadResult::get);
            assertThat(resultEvent.getEventId()).isEqualTo(eventId);
            assertThat(resultEvent.getConsumedAt()).isNotNull();
        } finally {
            startLatch.countDown();
            executorService.shutdownNow();
        }

        List<ConsumedEventEntity> recordsFromDb = retrieveAllRecordsFromConsumedEventTableWithJdbc();
        assertThat(recordsFromDb)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getEventId()).isEqualTo(eventId);
                    assertThat(record.getConsumedAt()).isNotNull();
                });
    }


    private List<ConsumedEventEntity> retrieveAllRecordsFromConsumedEventTableWithJdbc() {
        return jdbcTemplate.query(
                SELECT_ALL_FROM_CONSUMED_EVENT_TABLE,
                (resultSet, rowNum) -> ConsumedEventEntity.builder()
                        .eventId(resultSet.getObject("event_id", UUID.class))
                        .consumedAt(resultSet.getObject("consumed_at", OffsetDateTime.class))
                        .build());
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}