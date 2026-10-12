package priv.home.paymentservice.eventpublisher;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentEventPublishScheduler {


    private final PaymentEventReleaserService paymentEventReleaserService;


    @Scheduled(cron = "${payment-service.payment-event-publisher.scheduler.publish}") // TODO:configs?
    public void schedulePaymentEventPublishing() {
        log.info("START of Publishing of PaymentCreatedEvent");
        paymentEventReleaserService.publishPaymentCreatedEvents();
        log.info("END of Publishing of PaymentCreatedEvent");
    }
}
