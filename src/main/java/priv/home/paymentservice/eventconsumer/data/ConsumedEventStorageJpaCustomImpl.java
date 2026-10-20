package priv.home.paymentservice.eventconsumer.data;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
public class ConsumedEventStorageJpaCustomImpl implements ConsumedEventStorageJpaCustom {

    private static final String INSERT_CONSUMED_EVENT_ON_CONFLICT_DO_NOTHING = """
            INSERT INTO consumed_event (event_id)
            VALUES (?)
            ON CONFLICT (event_id) DO NOTHING
            RETURNING event_id, consumed_at
            """;

    private final JdbcTemplate jdbcTemplate;


    @Override
    public Optional<ConsumedEventEntity> insertConsumedEventIfAbsent(UUID consumedEventId) {
        return jdbcTemplate.query(
                INSERT_CONSUMED_EVENT_ON_CONFLICT_DO_NOTHING,
                resultSet -> resultSet.next() ?
                        Optional.of(ConsumedEventEntity.builder()
                                .eventId(resultSet.getObject("event_id", UUID.class))
                                .processedAt(resultSet.getObject("consumed_at", OffsetDateTime.class))
                                .build()) : Optional.empty(),
                consumedEventId);
    }
}
