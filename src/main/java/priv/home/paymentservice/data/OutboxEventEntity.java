package priv.home.paymentservice.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {


    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false, updatable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Short attemptCount = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;


    public void markAsPublished() {
        this.attemptCount++;
        this.publishedAt = OffsetDateTime.now();
    }

    public void markAsNotPublished(String errorInfo, int maxAttempts) {
        this.attemptCount++;
        this.lastError = errorInfo;
        if (this.attemptCount >= maxAttempts) {
            this.failedAt = OffsetDateTime.now();
        }
    }
}
