package priv.home.paymentservice.outbox.application;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
@ConditionalOnProperty(
        prefix = "payment-service.outbox.publisher",
        name = "publishing-mode",
        havingValue = "CONCURRENT"
)
public class PaymentEventPublishSchedulerConcurrent {

    private final PaymentEventReleaserServiceConcurrent paymentEventReleaserServiceConcurrent;


    @Scheduled(cron = "${payment-service.payment-event-publisher.scheduler.publish}")
    public void schedulePaymentEventPublishing() {
        log.info("START scheduled multi-thread publishing of PaymentCreatedEvent");
        paymentEventReleaserServiceConcurrent.publishPaymentCreatedEventsWithMultiWorkers();
    }
}
