package priv.home.paymentservice.payment.application;

import iso.std.iso._20022.tech.xsd.pain_001_001.Document;
import iso.std.iso._20022.tech.xsd.pain_002_001.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001PaymentData;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001PaymentMapper;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001XmlParser;
import priv.home.paymentservice.payment.iso20022.pain002.Pain002ResponseMapper;
import priv.home.paymentservice.payment.model.PaymentStatus;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePaymentPain001ServiceTest {

    private static final String IDEMPOTENCY_KEY_VALUE = "abc-123";
    private static final UUID CORRELATION_ID = UUID.fromString("e0eb0678-c0cc-48b6-9be7-c55c68e25cdb");
    private static final int PAYMENT_TRANSACTION_ONE = 1;
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("123.45");
    private static final String CURRENCY_PLN = "\"PLN\"";
    private static final String STATUS_PREFIX = "S-";
    private static final String PAIN001_VERSION = "pain.001.001.12";
    private static final String TRANSACTION_STATUS_REPORT_ACTC = "ACTC";
    private static final String PAYMENT_MESSAGE_ID = "MSG-20261104-001";
    private static final String PAYMENT_MESSAGE_END_TO_END_ID = "E2E-20261104-0001";
    private static final String PAYMENT_INFO_001 = "PMT-INF-001";

    @Mock
    private Pain001XmlParser pain001XmlParser;
    @Mock
    private Pain001PaymentMapper pain001PaymentMapper;
    @Mock
    private PaymentService paymentService;
    @Mock
    private Pain002ResponseMapper pain002ResponseMapper;

    @InjectMocks
    private CreatePaymentPain001Service createPaymentPain001Service;


    @Test
    public void shouldOrchestratePain001CreationFlow() {
        // given
        UUID paymentId = generateRandomUuid();
        byte[] exampleInputBytes = "<Document/>".getBytes(StandardCharsets.UTF_8);
        Document pain001Document = mock(Document.class);
        Pain001PaymentData pain001PaymentData = getCorrectPaymentData();
        CreatePaymentResult createPaymentResult = getSuccessfulPaymentCreationResponse(paymentId);
        iso.std.iso._20022.tech.xsd.pain_002_001.Document pain002StatusReport = buildPain002ReportDocument();
        ArgumentCaptor<Document> pain001DocumentArgumentCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<CreatePaymentRequest> createPaymentRequestArgumentCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<Pain001PaymentData> pain001PaymentDataArgumentCaptor = ArgumentCaptor.captor();

        when(pain001XmlParser.parse(any()))
                .thenReturn(pain001Document);
        when(pain001PaymentMapper.mapFromRequestPain001Document(pain001Document))
                .thenReturn(pain001PaymentData);
        when(paymentService.createPayment(any(), any(), any()))
                .thenReturn(createPaymentResult);
        when(pain002ResponseMapper.buildCustomerPaymentStatusReport(pain001PaymentData))
                .thenReturn(pain002StatusReport);


        // when
        CreatePain001Result createPain001Result = createPaymentPain001Service
                .createPaymentFromPain001Xml(CORRELATION_ID, IDEMPOTENCY_KEY_VALUE, exampleInputBytes);

        // then
        verify(pain001XmlParser, times(1))
                .parse(any(InputStream.class));
        verify(pain001PaymentMapper, times(1))
                .mapFromRequestPain001Document(pain001DocumentArgumentCaptor.capture());
        verify(paymentService, times(1))
                .createPayment(eq(CORRELATION_ID), eq(IDEMPOTENCY_KEY_VALUE), createPaymentRequestArgumentCaptor.capture());
        verify(pain002ResponseMapper, times(1))
                .buildCustomerPaymentStatusReport(pain001PaymentDataArgumentCaptor.capture());

        assertThat(pain001DocumentArgumentCaptor.getValue().getCstmrCdtTrfInitn())
                .isEqualTo(pain001Document.getCstmrCdtTrfInitn());
        assertThat(createPaymentRequestArgumentCaptor.getValue().amount())
                .isEqualTo(VALID_AMOUNT);
        assertThat(createPaymentRequestArgumentCaptor.getValue().currency())
                .isEqualTo(CURRENCY_PLN);
        assertThat(pain001PaymentDataArgumentCaptor.getValue().endToEndId())
                .isEqualTo(PAYMENT_MESSAGE_END_TO_END_ID);
        assertThat(pain001PaymentDataArgumentCaptor.getValue().paymentInformationId())
                .isEqualTo(PAYMENT_INFO_001);
        assertThat(pain001PaymentDataArgumentCaptor.getValue().messageId())
                .isEqualTo(PAYMENT_MESSAGE_ID);

        assertThat(createPain001Result.paymentId()).isEqualTo(paymentId);
        assertThat(createPain001Result.wasAlreadyInDb()).isFalse();
        assertThat(createPain001Result.createPain001PaymentStatusReportDocument()
                .getCstmrPmtStsRpt()
                .getOrgnlGrpInfAndSts()
                .getOrgnlMsgId()).isEqualTo(PAYMENT_MESSAGE_ID);
        assertThat(createPain001Result.createPain001PaymentStatusReportDocument()
                .getCstmrPmtStsRpt()
                .getOrgnlPmtInfAndSts()
                .getFirst()
                .getOrgnlPmtInfId()).isEqualTo(PAYMENT_INFO_001);
        assertThat(createPain001Result.createPain001PaymentStatusReportDocument()
                .getCstmrPmtStsRpt()
                .getOrgnlPmtInfAndSts()
                .getFirst()
                .getTxInfAndSts()
                .getFirst()
                .getOrgnlEndToEndId()).isEqualTo(PAYMENT_MESSAGE_END_TO_END_ID);
    }


    private Pain001PaymentData getCorrectPaymentData() {
        return Pain001PaymentData.builder()
                .messageId(PAYMENT_MESSAGE_ID)
                .creationDateTime(OffsetDateTime.parse("2026-11-04T10:00:00+01:00"))
                .transactionCount(PAYMENT_TRANSACTION_ONE)
                .paymentInformationId(PAYMENT_INFO_001)
                .endToEndId(PAYMENT_MESSAGE_END_TO_END_ID)
                .amount(VALID_AMOUNT)
                .currency(CURRENCY_PLN)
                .debtorName("Example debtor name")
                .debtorIban("DE89370400440532013000")
                .debtorBic("COBADEFFXXX")
                .creditorName("Example creditor name")
                .creditorIban("DE44500105175407324931")
                .creditorBic("DEUTDEFFXXX")
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

    private iso.std.iso._20022.tech.xsd.pain_002_001.Document buildPain002ReportDocument() {
        iso.std.iso._20022.tech.xsd.pain_002_001.Document pain002StatusReportDocument = new iso.std.iso._20022.tech.xsd.pain_002_001.Document();
        CustomerPaymentStatusReportV14 customerPaymentStatusReport = new CustomerPaymentStatusReportV14();
        GroupHeader128 groupHeader = new GroupHeader128();
        groupHeader.setMsgId(STATUS_PREFIX + generateRandomUuid().toString().replace("-", ""));
        groupHeader.setCreDtTm(toXmlGregorianCalendar(OffsetDateTime.now()));

        OriginalGroupHeader22 groupHeaderAndStatus = new OriginalGroupHeader22();
        groupHeaderAndStatus.setOrgnlMsgId(PAYMENT_MESSAGE_ID);
        groupHeaderAndStatus.setOrgnlMsgNmId(PAIN001_VERSION);

        OriginalPaymentInstruction51 paymentInstructionAndStatus = new OriginalPaymentInstruction51();
        paymentInstructionAndStatus.setOrgnlPmtInfId(PAYMENT_INFO_001);
        PaymentTransaction160 paymentTransaction = new PaymentTransaction160();
        paymentTransaction.setOrgnlEndToEndId(PAYMENT_MESSAGE_END_TO_END_ID);
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

    private UUID generateRandomUuid() {
        return UUID.randomUUID();
    }

}