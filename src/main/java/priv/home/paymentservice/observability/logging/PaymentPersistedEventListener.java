package priv.home.paymentservice.observability.logging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import priv.home.paymentservice.observability.metrics.PaymentMetrics;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPersistedEventListener {

    private final PaymentMetrics paymentMetrics;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLogging(PaymentPersistedEvent persistedEvent) {
        paymentMetrics.incrementPaymentsCreated();
        log.info(
                "Payment and outbox event successfully committed, paymentId={}, eventId={}",
                persistedEvent.paymentId(),
                persistedEvent.eventId()
        );
    }
}
