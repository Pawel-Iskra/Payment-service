package priv.home.paymentservice.eventconsumer.data;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@ToString
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "consumed_event")
public class ConsumedEventEntity {


    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;
}
