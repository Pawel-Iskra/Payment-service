package priv.home.paymentservice.payment.iso20022.pain001;

import iso.std.iso._20022.tech.xsd.pain_001_001.CreditTransferTransaction34;
import iso.std.iso._20022.tech.xsd.pain_001_001.CustomerCreditTransferInitiationV09;
import iso.std.iso._20022.tech.xsd.pain_001_001.Document;
import iso.std.iso._20022.tech.xsd.pain_001_001.PaymentInstruction30;
import jakarta.xml.bind.JAXBException;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import priv.home.paymentservice.payment.iso20022.Pain001TestData;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pain001XmlParserTest {

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
    public void shouldParseCorrectXml() throws SAXException, JAXBException {
        // given
        Pain001XmlParser pain001XmlParser = new Pain001XmlParser();
        InputStream xmlInputStream = new ByteArrayInputStream(
                Pain001TestData.getPain001SinglePaymentCorrectXml().getBytes());
        // when
        Document parseResult = pain001XmlParser.parse(xmlInputStream);

        // then
        assertThat(parseResult).isNotNull();
        assertThat(parseResult.getCstmrCdtTrfInitn()).isNotNull();
        assertThat(parseResult.getCstmrCdtTrfInitn().getPmtInf()).isNotNull();
        assertThat(parseResult.getCstmrCdtTrfInitn().getGrpHdr()).isNotNull();

        CustomerCreditTransferInitiationV09 crtCrdtTrfInit = parseResult.getCstmrCdtTrfInitn();
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
    public void shouldRejectNotCorrectXml() throws JAXBException, SAXException {
        // given
        Pain001XmlParser pain001XmlParser = new Pain001XmlParser();
        InputStream xmlInputStream = new ByteArrayInputStream(
                Pain001TestData.getPain001SinglePaymentNotCorrectXml().getBytes());

        // when + then
        assertThatThrownBy(() -> pain001XmlParser.parse(xmlInputStream))
                .isInstanceOf(InvalidPain001XmlException.class)
                .hasStackTraceContaining("Pain001 XML does not match the supported profile");
    }
}