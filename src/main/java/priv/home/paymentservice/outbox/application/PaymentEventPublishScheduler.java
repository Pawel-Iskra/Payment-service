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
        prefix = "payment-service.payment-event-publisher.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
) // controls bean creation by boolean property value in .properties
public class PaymentEventPublishScheduler {


    private final PaymentEventReleaserService paymentEventReleaserService;


    @Scheduled(cron = "${payment-service.payment-event-publisher.scheduler.publish}")
    public void schedulePaymentEventPublishing() {
        log.info("START scheduled publishing of PaymentCreatedEvent");
        paymentEventReleaserService.publishPaymentCreatedEvents();
    }
}
