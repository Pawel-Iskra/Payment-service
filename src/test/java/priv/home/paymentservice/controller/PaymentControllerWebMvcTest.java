package priv.home.paymentservice.controller;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import priv.home.paymentservice.dto.PaymentCreationDto;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(PaymentController.class)
public class PaymentControllerWebMvcTest {

    private static final String IDEMPOTENCY_KEY_NAME = "idempotency-key";
    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String NOT_FOUND_MESSAGE = "Not found payment with given id = %s";
    private static final String CURRENCY_PLN = "\"PLN\"";
    private static final String POST_PATH = "/payments";
    private static final String GET_PATH = "/payments/{id}";
    private static final String JSON_REQUEST = """
            {
                "amount": %s,
                "currency": %s
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
        String jsonRequest = getRequestJsonWithValues(VALID_AMOUNT.toString(), CURRENCY_PLN);
        PaymentCreationDto paymentCreationDto = getSuccessfullPaymentCreationResponse(
                getValidPaymentFromRequest(gePaymentRequest(VALID_AMOUNT, CURRENCY_PLN), paymentId));
        when(paymentService.createPayment(any(String.class), any(PaymentRequest.class))).thenReturn(paymentCreationDto);

        // then
        mockMvc.perform(post(POST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest)
                        .header(IDEMPOTENCY_KEY_NAME, IDEMPOTENCY_KEY_VALUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(VALID_AMOUNT.doubleValue()))
                .andExpect(jsonPath("$.currency").value(CURRENCY_PLN))
                .andExpect(jsonPath("$.paymentStatus").value(PaymentStatus.CREATED.toString()));
    }

    @Test
    public void shouldReturnHttpOkForCreateAlreadyExistingInDbPayment() throws Exception {
        // given
        UUID paymentId = generateUuid();
        String jsonRequest = getRequestJsonWithValues(VALID_AMOUNT.toString(), CURRENCY_PLN);
        PaymentCreationDto paymentCreationDto = getPaymentAlreadyExistPaymentCreationResponse(
                getValidPaymentFromRequest(gePaymentRequest(VALID_AMOUNT, CURRENCY_PLN), paymentId));
        when(paymentService.createPayment(any(String.class), any(PaymentRequest.class))).thenReturn(paymentCreationDto);

        // then
        mockMvc.perform(post(POST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest)
                        .header(IDEMPOTENCY_KEY_NAME, IDEMPOTENCY_KEY_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(VALID_AMOUNT.doubleValue()))
                .andExpect(jsonPath("$.currency").value(CURRENCY_PLN))
                .andExpect(jsonPath("$.paymentStatus").value(PaymentStatus.CREATED.toString()));
    }

    @Test
    public void shouldReturnHttpBadRequestForInvalidRequest() throws Exception {
        // given
        String invalidJsonRequest = getRequestJsonWithValues("0", CURRENCY_PLN);

        // then
        mockMvc.perform(post(POST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJsonRequest)
                        .header(IDEMPOTENCY_KEY_NAME, IDEMPOTENCY_KEY_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void shouldReturnHttpNotFoundForNotExistingPayment() throws Exception {
        // given
        String paymentId = generateUuid().toString();

        // then
        mockMvc.perform(get(GET_PATH, paymentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value("404 NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(String.format(NOT_FOUND_MESSAGE, paymentId)));
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(VALID_AMOUNT.doubleValue()))
                .andExpect(jsonPath("$.currency").value(CURRENCY_PLN))
                .andExpect(jsonPath("$.paymentStatus").value(PaymentStatus.CREATED.toString()));
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
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }


    private PaymentCreationDto getPaymentAlreadyExistPaymentCreationResponse(Payment payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private PaymentCreationDto getSuccessfullPaymentCreationResponse(Payment payment) {
        return PaymentCreationDto.builder()
                .paymentId(payment.getPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(PaymentStatus.CREATED)
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private PaymentRequest gePaymentRequest(BigDecimal amount, String currency) {
        return new PaymentRequest(amount, currency);
    }
}
