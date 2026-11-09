package priv.home.paymentservice.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.api.errorhandling.ErrorResponse;
import priv.home.paymentservice.payment.model.PaymentStatus;
import priv.home.paymentservice.payment.persistence.PaymentEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RetrievePaymentE2ETest extends AbstractE2ETest {

    private static final String API_GET_PATH = "/payments/%s";
    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final String INVALID_UUID_STRING = "Invalid UUID string: %s";
    private static final String NOT_FOUND_PAYMENT_WITH_GIVEN_ID = "Not found payment with given id = %s";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("12345.67");
    private static final String CURRENCY_PLN = "PLN";
    private static final String END_TO_END_ID_1 = "E2E-20261108-001";
    private static final String END_TO_END_ID_2 = "E2E-20261108-002";
    private static final String DEBTOR_IBAN_1 = "PL93109024023152899461927776";
    private static final String DEBTOR_IBAN_2 = "PL93109024023152899461927799";
    private static final String CREDITOR_IBAN_1 = "PL82109024029148361863856384";
    private static final String CREDITOR_IBAN_2 = "PL82109024029148361863856399";


    @Test
    public void shouldReturnHttpOkAndCorrectResponse() {
        // given
        UUID idFirst = generateRandomUuid();
        UUID idSecond = generateRandomUuid();
        BigDecimal[] amounts = new BigDecimal[]{new BigDecimal("10500.50"), new BigDecimal("3300.50")};
        String[] currencies = new String[]{"USD", "PLN"};
        String[] idempotencyKeys = new String[]{"abc-1", "abc-2"};
        String[] endToEndIds = new String[]{END_TO_END_ID_1, END_TO_END_ID_2};
        String[] debtorIbans = new String[]{DEBTOR_IBAN_1, DEBTOR_IBAN_2};
        String[] creditorIbans = new String[]{CREDITOR_IBAN_1, CREDITOR_IBAN_2};
        PaymentEntity paymentFirst = getPaymentEntity(idFirst, amounts[0], currencies[0], idempotencyKeys[0],
                endToEndIds[0], debtorIbans[0], creditorIbans[0]);
        PaymentEntity paymentSecond = getPaymentEntity(idSecond, amounts[1], currencies[1], idempotencyKeys[1],
                endToEndIds[1], debtorIbans[1], creditorIbans[1]);
        paymentStorageJpa.saveAll(List.of(paymentFirst, paymentSecond));

        // when
        ResponseEntity<PaymentResponse> response = testRestTemplate.getForEntity(
                API_GET_PATH.formatted(idSecond),
                PaymentResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().paymentId()).isEqualTo(idSecond);
        assertThat(response.getBody().paymentId()).isNotEqualTo(idFirst);
        assertThat(response.getBody().amount()).isEqualTo(amounts[1]);
        assertThat(response.getBody().currency()).isEqualTo(currencies[1]);
        assertThat(response.getBody().endToEndId()).isEqualTo(endToEndIds[1]);
        assertThat(response.getBody().debtorIban()).isEqualTo(debtorIbans[1]);
        assertThat(response.getBody().creditorIban()).isEqualTo(creditorIbans[1]);
        assertThat(response.getBody().paymentStatus()).isEqualTo(PaymentStatus.CREATED);

    }

    @Test
    public void shouldReturnHttpNotFoundForNotExistingPaymentId() {
        // given
        UUID paymentId = generateRandomUuid();
        UUID notExistingPaymentId = generateRandomUuid();
        PaymentEntity paymentEntity = getPaymentEntity(paymentId, VALID_AMOUNT, CURRENCY_PLN, IDEMPOTENCY_KEY_VALUE,
                END_TO_END_ID_1, DEBTOR_IBAN_1, CREDITOR_IBAN_1);
        paymentStorageJpa.save(paymentEntity);

        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                API_GET_PATH.formatted(notExistingPaymentId),
                ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo(NOT_FOUND_PAYMENT_WITH_GIVEN_ID.formatted(notExistingPaymentId));
    }

    @Test
    public void shouldReturnHttpBadRequestForNotCorrectIdFormat() {
        // given
        String notCorrectId = generateRandomUuid()
                .toString()
                .replace("-", "");

        // when
        ResponseEntity<ErrorResponse> response = testRestTemplate.getForEntity(
                API_GET_PATH.formatted(notCorrectId),
                ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains(INVALID_UUID_STRING.formatted(notCorrectId));
    }


    private PaymentEntity getPaymentEntity(UUID id, BigDecimal amount, String currency, String idempotencyKey,
                                           String endToEndId, String debtorIban, String creditorIban) {
        return PaymentEntity.builder()
                .paymentId(id)
                .amount(amount)
                .endToEndId(endToEndId)
                .debtorIban(debtorIban)
                .creditorIban(creditorIban)
                .currency(currency)
                .paymentStatus(PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private UUID generateRandomUuid() {
        return UUID.randomUUID();
    }
}
