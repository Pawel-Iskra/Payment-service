package priv.home.paymentservice.payment.iso20022.pain001;

import iso.std.iso._20022.tech.xsd.pain_001_001.*;
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
import static org.junit.jupiter.api.Assertions.*;

class Pain001PaymentMapperTest {

    private static final String SINGLE_PAYMENT_PAIN001_SAMPLE_XML_PATH = "/iso20022/pain001-single-payment.xml";
    private static final String LIMITED_PAIN001_XSD_PATH = "/iso20022/xsd/limited-pain001.xsd";

    private final Pain001PaymentMapper pain001PaymentMapper = new Pain001PaymentMapper();


    @Test
    public void shouldMapSelectedPaymentFields() throws SAXException, JAXBException, IOException {
        // given
        Document pain001Document;
        URL singlePaymentPain001XmlUrl = getClass().getResource(SINGLE_PAYMENT_PAIN001_SAMPLE_XML_PATH);
        URL xsdLimitedPain001SchemaUrl = getClass().getResource(LIMITED_PAIN001_XSD_PATH);
        assertThat(xsdLimitedPain001SchemaUrl).isNotNull();
        assertThat(singlePaymentPain001XmlUrl).isNotNull();
        Schema isoPain001OfficialStandardSchema = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(xsdLimitedPain001SchemaUrl);
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setSchema(isoPain001OfficialStandardSchema);
        try (InputStream xmlInputStream = singlePaymentPain001XmlUrl.openStream()) {
            JAXBElement<Document> root = unmarshaller.unmarshal(
                    new StreamSource(xmlInputStream), Document.class);
            pain001Document = root.getValue();
        }

        // when
        Pain001PaymentData resultPain001Data = pain001PaymentMapper.mapFromRequestPain001Document(pain001Document);
        CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation = pain001Document.getCstmrCdtTrfInitn();
        GroupHeader114 groupHeader = ctrCrdtTrfInitiation.getGrpHdr();
        PaymentInstruction44 paymentInstrInfo = ctrCrdtTrfInitiation.getPmtInf().getFirst();
        CreditTransferTransaction61 crdtTrfTransaction = paymentInstrInfo.getCdtTrfTxInf().getFirst();

        // then
        assertThat(resultPain001Data.amount()).isEqualTo(crdtTrfTransaction.getAmt().getInstdAmt().getValue());
        assertThat(resultPain001Data.currency()).isEqualTo(crdtTrfTransaction.getAmt().getInstdAmt().getCcy());
        assertThat(resultPain001Data.messageId()).isEqualTo(groupHeader.getMsgId());
        assertThat(resultPain001Data.endToEndId()).isEqualTo(crdtTrfTransaction.getPmtId().getEndToEndId());
        //TODO: rest values
//        assertThat(resultPain001Data.creationDateTime()).isEqualTo(groupHeader.getCreDtTm());

    }

}