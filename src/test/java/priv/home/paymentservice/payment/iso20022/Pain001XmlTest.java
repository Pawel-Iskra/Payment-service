package priv.home.paymentservice.payment.iso20022;

import iso.std.iso._20022.tech.xsd.pain_001_001.CreditTransferTransaction34;
import iso.std.iso._20022.tech.xsd.pain_001_001.CustomerCreditTransferInitiationV09;
import iso.std.iso._20022.tech.xsd.pain_001_001.Document;
import iso.std.iso._20022.tech.xsd.pain_001_001.PaymentInstruction30;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * The limited-pain001.xsd is the limited version of EPC132-08_2025_V1.0_pain.001.001.09.xsd.
 * The limited-pain001.xsd is the api contract for POST/pain001.
 * The limited-pain001.xsd represents a single payment instruction and a single credit transfer transaction.
 *
 * The fixture retains the official EPC message structure and all schema-required elements
 * while populating only the fields selected for this project.
 *
 * This is an educational implementation of a limited subset and does
 * not claim full ISO 20022 or SEPA business-rule compliance.
 */
public class Pain001XmlTest {

    // TODO: refactor
    private static final String PAIN001_ISO_EPC_STANDARD_XSD_PATH = "/iso20022/xsd/EPC132-08_2025_V1.0_pain.001.001.09.xsd";
    private static final String LIMITED_PAIN001_XSD_PATH = "/iso20022/xsd/limited-pain001.xsd";
    private static final String SINGLE_PAYMENT_PAIN001_XML_PATH = "/iso20022/pain001-single-payment.xml";
    // expected values from pain001-single-payment.xml
    private static final String GROUP_HEADER_MESSAGE_ID = "MSG-20261027-001";
    private static final String GROUP_HEADER_NUMBER_OF_TRANSACTION = "1";
    private static final String PAYMENT_INFO_DEBTOR_NAME = "Example Debtor Ltd";
    private static final String PAYMENT_INFO_DEBTOR_ACCOUNT_IBAN = "DE89370400440532013000";
    private static final String PAYMENT_END_TO_END_ID = "E2E-20261027-0001";
    private static final String PAYMENT_AMOUNT = "125.50";
    private static final String PAYMENT_CURRENCY = "EUR";
    private static final String CREDITOR_NAME = "Example Creditor GmbH";
    private static final String CREDITOR_ACCOUNT_ID = "DE44500105175407324931";


    @Test
    public void shouldValidatePain001SinglePaymentSchemaAndSelectedFields() throws SAXException, JAXBException, IOException {
        // given
        URL isoEpcPain001XsdUrl = getClass().getResource(PAIN001_ISO_EPC_STANDARD_XSD_PATH);
        URL singlePaymentPain001XmlUrl = getClass().getResource(SINGLE_PAYMENT_PAIN001_XML_PATH);
        assertThat(isoEpcPain001XsdUrl).isNotNull();
        assertThat(singlePaymentPain001XmlUrl).isNotNull();

        Schema isoPain001EpcStandardSchema = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(isoEpcPain001XsdUrl);
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setSchema(isoPain001EpcStandardSchema);  // Enable validation against the original EPC XSD.

        // when
        Document document;
        try (InputStream xmlInputStream = singlePaymentPain001XmlUrl.openStream()) {
            JAXBElement<Document> root = unmarshaller.unmarshal(
                    new StreamSource(xmlInputStream), Document.class);
            document = root.getValue();
        }

        // then
        CustomerCreditTransferInitiationV09 crtCrdtTrfInit = document.getCstmrCdtTrfInitn();
        assertThat(crtCrdtTrfInit).isNotNull();

        assertThat(crtCrdtTrfInit.getGrpHdr().getMsgId())
                .isNotNull()
                .isEqualTo(GROUP_HEADER_MESSAGE_ID);
        assertThat(crtCrdtTrfInit.getGrpHdr().getNbOfTxs())
                .isEqualTo(GROUP_HEADER_NUMBER_OF_TRANSACTION);

        assertThat(crtCrdtTrfInit.getPmtInf()).hasSize(1);
        PaymentInstruction30 paymentInfo = crtCrdtTrfInit.getPmtInf().getFirst();
        assertThat(paymentInfo.getDbtr()).isNotNull();
        assertThat(paymentInfo.getDbtr().getNm()).isEqualTo(PAYMENT_INFO_DEBTOR_NAME);
        assertThat(paymentInfo.getDbtrAcct()).isNotNull();
        assertThat(paymentInfo.getDbtrAcct().getId()).isNotNull();
        assertThat(paymentInfo.getDbtrAcct().getId().getIBAN()).isEqualTo(PAYMENT_INFO_DEBTOR_ACCOUNT_IBAN);
        assertThat(paymentInfo.getCdtTrfTxInf()).hasSize(1);

        CreditTransferTransaction34 crdTrfTxInf = paymentInfo.getCdtTrfTxInf().getFirst();
        assertThat(crdTrfTxInf.getPmtId()).isNotNull();
        assertThat(crdTrfTxInf.getPmtId().getEndToEndId()).isEqualTo(PAYMENT_END_TO_END_ID);
        assertThat(crdTrfTxInf.getAmt()).isNotNull();
        assertThat(crdTrfTxInf.getAmt().getInstdAmt().getValue()).isEqualTo(PAYMENT_AMOUNT);
        assertThat(crdTrfTxInf.getAmt().getInstdAmt().getCcy()).isEqualTo(PAYMENT_CURRENCY);
        assertThat(crdTrfTxInf.getCdtr().getNm()).isEqualTo(CREDITOR_NAME);
        assertThat(crdTrfTxInf.getCdtrAcct().getId().getIBAN()).isEqualTo(CREDITOR_ACCOUNT_ID);
    }

    @Test
    public void shouldBeValidAgainstBothSchemas() throws SAXException, IOException {
        assertValidAgainstSchema(PAIN001_ISO_EPC_STANDARD_XSD_PATH);
        assertValidAgainstSchema(LIMITED_PAIN001_XSD_PATH);
    }

    private void assertValidAgainstSchema(String xsdToValidateAgainstPath) throws SAXException, IOException {
        URL xsdSchemaToValidateAgainstUrl = getClass().getResource(xsdToValidateAgainstPath);
        URL singlePaymentPain001XmlUrl = getClass().getResource(SINGLE_PAYMENT_PAIN001_XML_PATH);
        assertNotNull(xsdToValidateAgainstPath);
        assertNotNull(singlePaymentPain001XmlUrl);

        Schema schemaXsdToValidateAgainst = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(xsdSchemaToValidateAgainstUrl);

        schemaXsdToValidateAgainst
                .newValidator()
                .validate(new StreamSource(singlePaymentPain001XmlUrl.toExternalForm()));
    }

}
