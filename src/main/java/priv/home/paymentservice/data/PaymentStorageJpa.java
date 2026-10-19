package priv.home.paymentservice.data;

import org.springframework.data.jpa.repository.JpaRepository;
import priv.home.paymentservice.model.PaymentEntity;

import java.util.Optional;
import java.util.UUID;

public interface PaymentStorageJpa extends JpaRepository<PaymentEntity, UUID>, PaymentStorageJpaCustom {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
}
