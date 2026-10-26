package priv.home.paymentservice.eventconsumer.persistence;

import java.util.Optional;
import java.util.UUID;

public interface ConsumedEventStorageJpaCustom {

    Optional<ConsumedEventEntity> insertConsumedEventIfAbsent(UUID consumedEventId);
}
