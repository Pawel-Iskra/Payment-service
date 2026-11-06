package priv.home.paymentservice.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import priv.home.paymentservice.eventconsumer.persistence.ConsumedEventEntity;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
public class PaymentJsonE2EProcessingTest extends AbstractE2ETest {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String API_PATH = "/payments";
    private static final String PAYMENT_EVENT_CONSUMED = "Consumed payment event=%s";
    private static final String EVENT_SUCCESSFULLY_PROCESSED = "Payment event successfully processed, consumedEventEntity=%s";
    private static final String EVENT_NOT_PROCESSED = "Payment event was not processed (duplicate or already registered)";


    @Test
    public void shouldPublishPaymentCreatedEventToKafka(CapturedOutput capturedOutput){
        // given
        BigDecimal amount = new BigDecimal("1500.50");
        String currency = "USD";
        String idempotencyKey = "abcd-1234";
        CreatePaymentRequest paymentRequest = getCreatePaymentRequest(amount, currency);
        HttpHeaders requestHeaders = getRequestHeaders(MediaType.APPLICATION_JSON, idempotencyKey);
        HttpEntity<CreatePaymentRequest> httpRequest = new HttpEntity<>(paymentRequest, requestHeaders);

        // when
        ResponseEntity<PaymentResponse> response = testRestTemplate.postForEntity(
                API_PATH,
                httpRequest,
                PaymentResponse.class);

        // then
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    assertThat(consumedEventStorageJpa.findAll()).isNotEmpty();
                });
        List<ConsumedEventEntity> consumedEventEntities = consumedEventStorageJpa.findAll();
        assertThat(consumedEventEntities).hasSize(1);
        assertThat(capturedOutput.getOut()).contains(PAYMENT_EVENT_CONSUMED.formatted(""));
        assertThat(capturedOutput.getOut()).contains(EVENT_SUCCESSFULLY_PROCESSED.formatted(""));

    }

    private CreatePaymentRequest getCreatePaymentRequest(BigDecimal amount, String currency) {
        return CreatePaymentRequest.builder()
                .amount(amount)
                .currency(currency)
                .build();
    }

    private HttpHeaders getRequestHeaders(MediaType mediaType, String idempotencyKey) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(mediaType);
        httpHeaders.set(IDEMPOTENCY_KEY, idempotencyKey);
        return httpHeaders;
    }

}
