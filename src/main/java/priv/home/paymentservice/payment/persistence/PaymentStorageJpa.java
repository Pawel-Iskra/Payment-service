package priv.home.paymentservice.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentStorageJpa extends JpaRepository<PaymentEntity, UUID>, PaymentStorageJpaCustom {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
}
