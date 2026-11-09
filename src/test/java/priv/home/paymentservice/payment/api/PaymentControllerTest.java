package priv.home.paymentservice.payment.api;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.CreditorRequest;
import priv.home.paymentservice.payment.api.dto.DebtorRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.api.mapper.PaymentJsonResponseMapper;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.application.PaymentRetrieveResult;
import priv.home.paymentservice.payment.application.PaymentService;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;
import priv.home.paymentservice.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("1234.56");
    private static final String CURRENCY_PLN = "PLN";
    private static final String END_TO_END_ID = "E2E-20261108-001";
    private static final String DEBTOR_IBAN = "PL93109024023152899461927776";
    private static final String CREDITOR_IBAN = "PL82109024029148361863856384";
    private static final DebtorRequest DEBTOR_REQUEST = new DebtorRequest("Debtor Name", DEBTOR_IBAN);
    private static final CreditorRequest CREDITOR_REQUEST = new CreditorRequest("Creditor Name", CREDITOR_IBAN);

    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentJsonResponseMapper paymentJsonResponseMapper;
    @InjectMocks
    private PaymentController underTest;

    @BeforeEach
    void setUpRequestContext() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setScheme(MockHttpServletRequest.DEFAULT_SCHEME);
        mockRequest.setServerName("localhost");
        mockRequest.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @AfterEach
    void clearMvcContext() {
        RequestContextHolder.resetRequestAttributes();
    }


    @Test
    public void whenPaymentAddedSuccessfullyThenShouldBeProperResponse() {
        // given
        CreatePaymentRequest properCreatePaymentRequest = getProperPaymentRequest();
        CreatePaymentResult createPaymentResult = getPaymentCreateResult(properCreatePaymentRequest, false);
        PaymentResponse paymentResponse = getPaymentResponse(createPaymentResult);
        when(paymentService.createPayment(any(), any()))
                .thenReturn(createPaymentResult);
        when(paymentJsonResponseMapper.buildPaymentJsonResponse(any(CreatePaymentResult.class)))
                .thenReturn(paymentResponse);

        // when
        ResponseEntity<PaymentResponse> result = underTest.createPayment(IDEMPOTENCY_KEY, properCreatePaymentRequest);

        // then
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(result.getBody()).isNotNull();
    }

    @Test
    public void shouldReturnHttpOKForExistingPayment() {
        // given
        PaymentRetrieveResult paymentRetrieveResult = getPaymentRetrieveResult();
        when(paymentService.retrieveSinglePaymentByPaymentId(any()))
                .thenReturn(Optional.of(paymentRetrieveResult));

        // when
        ResponseEntity<PaymentResponse> result = underTest.retrievePayment(paymentRetrieveResult.paymentId());

        // then
        Assertions.assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }


    @Test
    public void shouldThrowExceptionForNotExistingPayment() {
        // given
        when(paymentService.retrieveSinglePaymentByPaymentId(any())).thenReturn(Optional.empty());

        // when + then
        SinglePaymentNotFoundException exceptionThrown = assertThrows(
                SinglePaymentNotFoundException.class, () -> underTest.retrievePayment(generateUuid()));
        Assertions.assertThat(exceptionThrown.getMessage()).contains("Not found payment with given id");
    }


    private CreatePaymentRequest getProperPaymentRequest() {
        return CreatePaymentRequest.builder()
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .debtorRequest(DEBTOR_REQUEST)
                .creditorRequest(CREDITOR_REQUEST)
                .endToEndId(END_TO_END_ID)
                .build();
    }

    public PaymentResponse getPaymentResponse(CreatePaymentResult createPaymentResult) {
        return PaymentResponse.builder()
                .paymentId(createPaymentResult.paymentId())
                .endToEndId(createPaymentResult.endToEndId())
                .debtorIban(createPaymentResult.debtorIban())
                .creditorIban(createPaymentResult.creditorIban())
                .amount(createPaymentResult.amount())
                .currency(createPaymentResult.currency())
                .paymentStatus(createPaymentResult.paymentStatus())
                .build();
    }

    private CreatePaymentResult getPaymentCreateResult(CreatePaymentRequest createPaymentRequest, boolean wasAlreadyInDb) {
        return CreatePaymentResult.builder()
                .paymentId(generateUuid())
                .endToEndId(createPaymentRequest.endToEndId())
                .debtorIban(createPaymentRequest.debtorRequest().debtorIban())
                .creditorIban(createPaymentRequest.creditorRequest().creditorIban())
                .amount(createPaymentRequest.amount())
                .currency(createPaymentRequest.currency())
                .paymentStatus(PaymentStatus.CREATED)
                .wasAlreadyInDb(wasAlreadyInDb)
                .build();
    }

    private PaymentRetrieveResult getPaymentRetrieveResult() {
        return PaymentRetrieveResult.builder()
                .paymentId(generateUuid())
                .endToEndId(END_TO_END_ID)
                .debtorIban(DEBTOR_IBAN)
                .creditorIban(CREDITOR_IBAN)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }

}