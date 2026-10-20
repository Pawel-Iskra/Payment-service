package priv.home.paymentservice.eventconsumer.data;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity {


    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
