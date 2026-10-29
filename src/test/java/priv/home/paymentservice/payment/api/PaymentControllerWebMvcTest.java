package priv.home.paymentservice.payment.api;


import iso.std.iso._20022.tech.xsd.pain_002_001.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.application.CreatePain001Result;
import priv.home.paymentservice.payment.application.CreatePaymentPain001Service;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.application.PaymentService;
import priv.home.paymentservice.payment.iso20022.Pain001TestData;
import priv.home.paymentservice.payment.model.PaymentStatus;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(PaymentController.class)
public class PaymentControllerWebMvcTest {

    // data for create pain001
    private static final String POST_PAIN001_PATH = "/payments/pain001";
    private static final String STATUS_PREFIX = "S-";
    private static final String PAIN001_VERSION = "pain.001.001.12";
    private static final String TRANSACTION_STATUS_REPORT_ACTC = "ACTC";

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
    @MockitoBean
    private CreatePaymentPain001Service createPaymentPain001Service;


    @Test
    public void shouldReturnHttpCreatedForProperRequest() throws Exception {
        // given
        UUID paymentId = generateUuid();
        String jsonRequest = getRequestJsonWithValues(VALID_AMOUNT);
        CreatePaymentResult createPaymentResult = getSuccessfulPaymentCreationResponse(paymentId);
        when(paymentService.createPayment(any(String.class), any(CreatePaymentRequest.class)))
                .thenReturn(createPaymentResult);

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
        String jsonRequest = getRequestJsonWithValues(VALID_AMOUNT);
        CreatePaymentResult createPaymentResult = getPaymentAlreadyExistPaymentCreationResponse(paymentId);
        when(paymentService.createPayment(any(String.class), any(CreatePaymentRequest.class)))
                .thenReturn(createPaymentResult);

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
        String invalidJsonRequest = getRequestJsonWithValues(BigDecimal.ZERO);

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
        PaymentResponse paymentResponse = getPaymentResponse(paymentId);
        when(paymentService.retrieveSinglePaymentByPaymentId(any()))
                .thenReturn(Optional.of(paymentResponse));

        // then
        mockMvc.perform(get(GET_PATH, paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.amount").value(VALID_AMOUNT.doubleValue()))
                .andExpect(jsonPath("$.currency").value(CURRENCY_PLN))
                .andExpect(jsonPath("$.paymentStatus").value(PaymentStatus.CREATED.toString()));
    }

    @Test
    public void shouldReturnHttpCreatedAndCorrectPain002Report() throws Exception {
        // given
        UUID paymentId = generateUuid();
        CreatePain001Result createPain001Result = getCreatePain001Result(paymentId, false);
        when(createPaymentPain001Service.createPaymentFromPain001Xml(any(), any()))
                .thenReturn(createPain001Result);
        String requestStringXml = Pain001TestData.getPain001SinglePaymentCorrectXmlString();

        // then
        mockMvc.perform(post(POST_PAIN001_PATH)
                        .header(IDEMPOTENCY_KEY_NAME, IDEMPOTENCY_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .content(requestStringXml))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("payments/" + paymentId)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("//*[local-name()='TxInfAndSts']/*[local-name()='TxSts']")
                        .string(TRANSACTION_STATUS_REPORT_ACTC));
    }

    @Test
    public void shouldReturnHttpOkAndCorrectPain002Report() throws Exception {
        // given
        UUID paymentId = generateUuid();
        CreatePain001Result createPain001Result = getCreatePain001Result(paymentId, true);
        when(createPaymentPain001Service.createPaymentFromPain001Xml(any(), any()))
                .thenReturn(createPain001Result);
        String requestStringXml = Pain001TestData.getPain001SinglePaymentCorrectXmlString();

        // then
        mockMvc.perform(post(POST_PAIN001_PATH)
                        .header(IDEMPOTENCY_KEY_NAME, IDEMPOTENCY_KEY_VALUE)
                        .contentType(MediaType.APPLICATION_XML)
                        .accept(MediaType.APPLICATION_XML)
                        .content(requestStringXml))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("payments/" + paymentId)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(xpath("//*[local-name()='TxInfAndSts']/*[local-name()='TxSts']")
                        .string(TRANSACTION_STATUS_REPORT_ACTC));
    }


    private String getRequestJsonWithValues(BigDecimal amount) {
        return String.format(JSON_REQUEST, amount.toString(), CURRENCY_PLN);
    }

    private PaymentResponse getPaymentResponse(UUID paymentId) {
        return PaymentResponse.builder()
                .paymentId(paymentId)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }

    private CreatePaymentResult getPaymentAlreadyExistPaymentCreationResponse(UUID paymentId) {
        return CreatePaymentResult.builder()
                .paymentId(paymentId)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .wasAlreadyInDb(Boolean.TRUE)
                .build();
    }

    private CreatePaymentResult getSuccessfulPaymentCreationResponse(UUID paymentId) {
        return CreatePaymentResult.builder()
                .paymentId(paymentId)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .paymentStatus(PaymentStatus.CREATED)
                .wasAlreadyInDb(Boolean.FALSE)
                .build();
    }

    private CreatePain001Result getCreatePain001Result(UUID paymentId, boolean wasAlreadyInDb) {
        return CreatePain001Result.builder()
                .paymentId(paymentId)
                .wasAlreadyInDb(wasAlreadyInDb)
                .createPain001PaymentStatusReportDocument(buildPain002ReportDocument())
                .build();
    }

    private Document buildPain002ReportDocument() {
        Document pain002StatusReportDocument = new Document();
        CustomerPaymentStatusReportV14 customerPaymentStatusReport = new CustomerPaymentStatusReportV14();
        GroupHeader128 groupHeader = new GroupHeader128();
        groupHeader.setMsgId(STATUS_PREFIX + generateUuid().toString().replace("-", ""));
        groupHeader.setCreDtTm(toXmlGregorianCalendar(OffsetDateTime.now()));

        OriginalGroupHeader22 groupHeaderAndStatus = new OriginalGroupHeader22();
        groupHeaderAndStatus.setOrgnlMsgId("MSG-20261027-001");
        groupHeaderAndStatus.setOrgnlMsgNmId(PAIN001_VERSION);

        OriginalPaymentInstruction51 paymentInstructionAndStatus = new OriginalPaymentInstruction51();
        paymentInstructionAndStatus.setOrgnlPmtInfId("PMT-INF-001");
        PaymentTransaction160 paymentTransaction = new PaymentTransaction160();
        paymentTransaction.setOrgnlEndToEndId("E2E-20261027-0001");
        paymentTransaction.setTxSts(TRANSACTION_STATUS_REPORT_ACTC);
        paymentInstructionAndStatus.getTxInfAndSts().add(paymentTransaction);

        customerPaymentStatusReport.getOrgnlPmtInfAndSts().add(paymentInstructionAndStatus);
        customerPaymentStatusReport.setOrgnlGrpInfAndSts(groupHeaderAndStatus);
        customerPaymentStatusReport.setGrpHdr(groupHeader);
        pain002StatusReportDocument.setCstmrPmtStsRpt(customerPaymentStatusReport);
        return pain002StatusReportDocument;
    }

    private XMLGregorianCalendar toXmlGregorianCalendar(OffsetDateTime offsetDateTime) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(
                    offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (DatatypeConfigurationException exception) {
            throw new IllegalStateException("Can not create XML date-time value", exception);
        }
    }

    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}
