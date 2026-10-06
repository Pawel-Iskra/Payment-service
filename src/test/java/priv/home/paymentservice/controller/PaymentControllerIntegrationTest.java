package priv.home.paymentservice.controller;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import priv.home.paymentservice.dto.PaymentRequest;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;
import priv.home.paymentservice.service.PaymentService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(PaymentController.class)
public class PaymentControllerIntegrationTest {

    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "PLN";
    private static final String POST_PATH = "/payments";
    private static final String GET_PATH = "/payments/{id}";
    private static final String JSON_REQUEST = """
            {
                "amount": %s,
                "currency": "%s"
            }
            """;


    @Autowired
    protected MockMvc mockMvc;
    @MockitoBean
    private PaymentService paymentService;


    @Test
    public void shouldReturnHttpCreatedForProperRequest() throws Exception {
        // given
        UUID paymentId = generateUuid();
        String json_request = getRequestJsonWithValues(VALID_AMOUNT.toString(), CURRENCY_PLN);
        PaymentResponse paymentResponse = getPaymentResponse(
                getValidPaymentFromRequest(gePaymentRequest(VALID_AMOUNT, CURRENCY_PLN), paymentId));
        when(paymentService.createPayment(any(PaymentRequest.class))).thenReturn(paymentResponse);

        // then
        mockMvc.perform(post(POST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json_request))
                .andExpect(status().isCreated());
    }

    @Test
    public void shouldReturnHttpBadRequestForInvalidRequest() throws Exception {
        // given
        String invalid_json_request = getRequestJsonWithValues("0", CURRENCY_PLN);

        // then
        mockMvc.perform(post(POST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid_json_request))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldReturnHttpNotFoundForNotExistingPayment() throws Exception {
        // given
        String randomUuid = generateUuid().toString();

        // then
        mockMvc.perform(get(GET_PATH, randomUuid))
                .andExpect(status().isNotFound());
    }

    @Test
    public void shouldReturnHttpOkForExistingPayment() throws Exception {
        // given
        UUID paymentId = generateUuid();
        PaymentResponse paymentResponse = getPaymentResponse(
                getValidPaymentFromRequest(gePaymentRequest(VALID_AMOUNT, CURRENCY_PLN), paymentId));
        when(paymentService.retrieveSinglePaymentByPaymentId(any())).thenReturn(Optional.of(paymentResponse));

        // then
        mockMvc.perform(get(GET_PATH, paymentId))
                .andExpect(status().isOk());
    }


    private UUID generateUuid() {
        return UUID.randomUUID();
    }

    private String getRequestJsonWithValues(String amount, String currency) {
        return String.format(JSON_REQUEST, amount, currency);
    }

    private Payment getValidPaymentFromRequest(PaymentRequest validPaymentRequest, UUID paymentId) {
        return Payment.builder()
                .paymentId(paymentId)
                .amount(validPaymentRequest.amount())
                .currency(validPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private PaymentResponse getPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }

    private PaymentRequest gePaymentRequest(BigDecimal amount, String currency) {
        return new PaymentRequest(amount, currency);
    }
}
