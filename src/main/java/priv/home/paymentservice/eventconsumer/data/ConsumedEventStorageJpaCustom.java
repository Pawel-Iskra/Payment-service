package priv.home.paymentservice.eventconsumer.data;

import java.util.Optional;
import java.util.UUID;

public interface ConsumedEventStorageJpaCustom {

    Optional<ConsumedEventEntity> insertConsumedEventIfAbsent(UUID consumedEventId);
}
