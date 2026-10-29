package priv.home.paymentservice.payment.iso20022.pain002;

import iso.std.iso._20022.tech.xsd.pain_002_001.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001PaymentData;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.math.BigDecimal;
import java.net.URL;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Pain002ResponseMapperTest {

    private static final int PAYMENT_TRANSACTION_ONE = 1;
    private static final int GROUP_HEADER_MESSAGE_ID_LIMIT = 35;
    private static final String STATUS_PREFIX = "S-";
    private static final String PAIN001_VERSION = "pain.001.001.12";
    private static final String TRANSACTION_STATUS_ACTC = "ACTC";
    private static final String LIMITED_PAIN002_XSD_PATH = "/iso20022/xsd/limited-pain002.xsd";
    private static final String ISO_STANDARD_PAIN002_XSD_PATH = "/iso20022/xsd/pain.002.001.14.xsd";


    private final Pain002ResponseMapper pain002ResponseMapper = new Pain002ResponseMapper();


    @Test
    public void shouldBuildCorrectPain002Response() {
        // given
        OffsetDateTime timeBeforeMapperStart = OffsetDateTime.now().minusSeconds(1);
        Pain001PaymentData pain001PaymentData = getCorrectPaymentData();

        // when
        Document pain002Response = pain002ResponseMapper.buildCustomerPaymentStatusReport(pain001PaymentData);
        OffsetDateTime timeAfterMapperFinish = OffsetDateTime.now().plusSeconds(1);

        // then
        CustomerPaymentStatusReportV14 responseStatusReport = pain002Response.getCstmrPmtStsRpt();
        assertNotNull(responseStatusReport);
        GroupHeader128 groupHeader = responseStatusReport.getGrpHdr();
        OriginalGroupHeader22 groupHeaderAndStatus = responseStatusReport.getOrgnlGrpInfAndSts();
        assertNotNull(groupHeader);
        assertNotNull(groupHeaderAndStatus);
        assertNotNull(responseStatusReport.getOrgnlPmtInfAndSts());
        OriginalPaymentInstruction51 paymentInstructionAndStatus = responseStatusReport.getOrgnlPmtInfAndSts().getFirst();
        assertNotNull(paymentInstructionAndStatus);
        assertNotNull(paymentInstructionAndStatus.getTxInfAndSts());
        PaymentTransaction160 paymentTransaction = paymentInstructionAndStatus.getTxInfAndSts().getFirst();
        assertNotNull(paymentTransaction);

        assertThat(groupHeader.getMsgId()).startsWith(STATUS_PREFIX);
        assertThat(groupHeader.getMsgId()).hasSizeLessThanOrEqualTo(GROUP_HEADER_MESSAGE_ID_LIMIT);
        assertThat(getOffsetDateTime(groupHeader.getCreDtTm()))
                .isAfter(timeBeforeMapperStart)
                .isBefore(timeAfterMapperFinish);
        assertThat(groupHeaderAndStatus.getOrgnlMsgId()).isEqualTo(pain001PaymentData.messageId());
        assertThat(groupHeaderAndStatus.getOrgnlMsgNmId()).isEqualTo(PAIN001_VERSION);
        assertThat(paymentInstructionAndStatus.getOrgnlPmtInfId()).isEqualTo(pain001PaymentData.paymentInformationId());
        assertThat(paymentTransaction.getOrgnlEndToEndId()).isEqualTo(pain001PaymentData.endToEndId());
        assertThat(paymentTransaction.getTxSts()).isEqualTo(TRANSACTION_STATUS_ACTC);
    }

    @Test
    public void createdPain002ShouldBeValidAgainstLimitedPain002XsdAndAgainstOfficialIsoPain002Xsd() throws SAXException, JAXBException {
        // given
        Pain001PaymentData pain001PaymentData = getCorrectPaymentData();
        Marshaller limitedPain002marshaller = prepareJaxbMarshaller(LIMITED_PAIN002_XSD_PATH);
        Marshaller isoFullPain002marshaller = prepareJaxbMarshaller(ISO_STANDARD_PAIN002_XSD_PATH);

        // when
        Document pain002ResponseDocument = pain002ResponseMapper.buildCustomerPaymentStatusReport(pain001PaymentData);

        // then
        assertDoesNotThrow(() -> limitedPain002marshaller.marshal(
                new ObjectFactory().createDocument(pain002ResponseDocument),
                new DefaultHandler()));
        assertDoesNotThrow(() -> isoFullPain002marshaller.marshal(
                new ObjectFactory().createDocument(pain002ResponseDocument),
                new DefaultHandler()));
    }

    @Test
    public void shouldThrowExceptionWhenPain002ResponseGroupHeaderMessageIdIsNull() throws SAXException, JAXBException {
        // given
        Pain001PaymentData pain001PaymentData = getCorrectPaymentData();
        Marshaller limitedPain002marshaller = prepareJaxbMarshaller(LIMITED_PAIN002_XSD_PATH);

        // when
        Document pain002ResponseDocument = pain002ResponseMapper.buildCustomerPaymentStatusReport(pain001PaymentData);
        pain002ResponseDocument.getCstmrPmtStsRpt().getGrpHdr().setMsgId(null);

        // then
        assertThatThrownBy(() -> limitedPain002marshaller.marshal(
                new ObjectFactory().createDocument(pain002ResponseDocument),
                new DefaultHandler()))
                .isInstanceOf(JAXBException.class)
                .hasStackTraceContaining("MsgId");
    }

    @Test
    public void shouldThrowExceptionWhenPain002ResponseStatusIsOtherThanActc() throws SAXException, JAXBException {
        // given
        String responseStatusReject = "ACCP";
        Pain001PaymentData pain001PaymentData = getCorrectPaymentData();
        Marshaller limitedPain002marshaller = prepareJaxbMarshaller(LIMITED_PAIN002_XSD_PATH);

        // when
        Document pain002ResponseDocument = pain002ResponseMapper.buildCustomerPaymentStatusReport(pain001PaymentData);
        pain002ResponseDocument.getCstmrPmtStsRpt().getOrgnlPmtInfAndSts().getFirst()
                .getTxInfAndSts().getFirst()
                .setTxSts(responseStatusReject);

        // then
        assertThatThrownBy(() -> limitedPain002marshaller.marshal(
                new ObjectFactory().createDocument(pain002ResponseDocument),
                new DefaultHandler()))
                .isInstanceOf(JAXBException.class)
                .hasStackTraceContaining("Value '%s' is not facet-valid with respect to enumeration"
                        .formatted(responseStatusReject));
    }


    private Pain001PaymentData getCorrectPaymentData() {
        return Pain001PaymentData.builder()
                .messageId("MSG-20261027-001")
                .creationDateTime(OffsetDateTime.parse("2026-10-27T10:00:00+01:00"))
                .transactionCount(PAYMENT_TRANSACTION_ONE)
                .paymentInformationId("PMT-INF-001")
                .endToEndId("E2E-20261027-0001")
                .amount(new BigDecimal("125.50"))
                .currency("EUR")
                .debtorName("Example debtor name")
                .debtorIban("DE89370400440532013000")
                .debtorBic("COBADEFFXXX")
                .creditorName("Example creditor name")
                .creditorIban("DE44500105175407324931")
                .creditorBic("DEUTDEFFXXX")
                .build();
    }

    private OffsetDateTime getOffsetDateTime(XMLGregorianCalendar creationDateTime) {
        if (creationDateTime == null) {
            throw new IllegalStateException("Creation date time is null");
        }
        if (creationDateTime.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new IllegalStateException("Creation date time must contain offset");
        }
        return creationDateTime.toGregorianCalendar()
                .toZonedDateTime()
                .toOffsetDateTime();
    }

    private Marshaller prepareJaxbMarshaller(String xsdSchemaPath) throws SAXException, JAXBException {
        URL xsdSchemaUrl = getClass().getResource(xsdSchemaPath);
        assertNotNull(xsdSchemaUrl);
        Schema schemaFromXsdUrl = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(xsdSchemaUrl);
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setSchema(schemaFromXsdUrl);
        return marshaller;
    }

}