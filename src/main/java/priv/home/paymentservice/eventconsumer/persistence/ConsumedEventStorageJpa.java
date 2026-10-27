package priv.home.paymentservice.eventconsumer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedEventStorageJpa extends JpaRepository<ConsumedEventEntity, UUID>, ConsumedEventStorageJpaCustom {

}
