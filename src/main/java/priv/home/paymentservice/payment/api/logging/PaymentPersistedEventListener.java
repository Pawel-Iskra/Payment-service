package priv.home.paymentservice.payment.api.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class PaymentPersistedEventListener {


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLogging(PaymentPersistedEvent persistedEvent) {
        log.info(
                "Payment and outbox event successfully committed, paymentId={}, eventId={}",
                persistedEvent.paymentId(),
                persistedEvent.eventId()
        );
    }
}
