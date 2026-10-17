package priv.home.paymentservice.data;


import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import priv.home.paymentservice.eventpublisher.model.PaymentCreatedEvent;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Repository
@AllArgsConstructor
public class OutboxStorageJdbc {

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO outbox_event (event_id, aggregate_id, event_type, payload)
            VALUES (?, ?, ?, CAST(? AS JSONB))""";
    private static final String SELECT_EVENTS_TO_PUBLISH_SQL = """
            SELECT event_id, payload FROM outbox_event
            WHERE published_at is NULL""";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;


    public void addPaymentEventToOutbox(UUID eventId, UUID aggregate_id, String eventType, String payload) {
        jdbcTemplate.update(
                INSERT_EVENT_SQL,
                eventId,
                aggregate_id,
                eventType,
                payload
        );
    }

    public List<OutboxEventToPublish> retrieveEventsForPublish() {
        return jdbcTemplate.query(SELECT_EVENTS_TO_PUBLISH_SQL,
                (resultSet, rowNum) -> new OutboxEventToPublish(
                        resultSet.getObject("event_id", UUID.class),
                        objectMapper.readValue(resultSet.getString("payload"), PaymentCreatedEvent.class))
        );
    }

}
