package priv.home.paymentservice.payment.iso20022.pain001;

import iso.std.iso._20022.tech.xsd.pain_001_001.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import priv.home.paymentservice.payment.iso20022.Pain001TestData;

import javax.xml.XMLConstants;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pain001PaymentMapperTest {

    private static final String LIMITED_PAIN001_XSD_PATH = "/iso20022/xsd/limited-pain001.xsd";
    private static final int TRANSACTION_COUNT_ONE = 1;

    private final Pain001PaymentMapper pain001PaymentMapper = new Pain001PaymentMapper();


    @Test
    public void shouldMapSelectedPaymentFields() throws SAXException, JAXBException {
        // given
        Document pain001Document = preparePain001Document(Pain001TestData.getPain001SinglePaymentCorrectXmlString());

        // when
        Pain001PaymentData resultPain001Data = pain001PaymentMapper.mapFromRequestPain001Document(pain001Document);

        // then
        CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation = pain001Document.getCstmrCdtTrfInitn();
        GroupHeader114 groupHeader = ctrCrdtTrfInitiation.getGrpHdr();
        PaymentInstruction44 paymentInstrInfo = ctrCrdtTrfInitiation.getPmtInf().getFirst();
        CreditTransferTransaction61 crdtTrfTransaction = paymentInstrInfo.getCdtTrfTxInf();

        assertThat(resultPain001Data.messageId()).isEqualTo(groupHeader.getMsgId());
        assertThat(resultPain001Data.creationDateTime()).isEqualTo(getOffsetDateTime(groupHeader.getCreDtTm()));
        assertThat(resultPain001Data.transactionCount()).isEqualTo(TRANSACTION_COUNT_ONE);
        assertThat(resultPain001Data.paymentInformationId()).isEqualTo(paymentInstrInfo.getPmtInfId());
        assertThat(resultPain001Data.endToEndId()).isEqualTo(crdtTrfTransaction.getPmtId().getEndToEndId());
        assertThat(resultPain001Data.amount()).isEqualTo(crdtTrfTransaction.getAmt().getInstdAmt().getValue());
        assertThat(resultPain001Data.currency()).isEqualTo(crdtTrfTransaction.getAmt().getInstdAmt().getCcy());
        assertThat(resultPain001Data.debtorName()).isEqualTo(paymentInstrInfo.getDbtr().getNm());
        assertThat(resultPain001Data.debtorIban()).isEqualTo(paymentInstrInfo.getDbtrAcct().getId().getIBAN());
        assertThat(resultPain001Data.debtorBic()).isEqualTo(paymentInstrInfo.getDbtrAgt().getFinInstnId().getBICFI());
        assertThat(resultPain001Data.creditorName()).isEqualTo(crdtTrfTransaction.getCdtr().getNm());
        assertThat(resultPain001Data.creditorIban()).isEqualTo(crdtTrfTransaction.getCdtrAcct().getId().getIBAN());
        assertThat(resultPain001Data.creditorBic()).isEqualTo(crdtTrfTransaction.getCdtrAgt().getFinInstnId().getBICFI());
    }

    @Test
    public void shouldThrowExceptionWhenRequiredFieldNotPresent() throws JAXBException, SAXException {
        // given
        Document pain001Document = preparePain001Document(Pain001TestData.getPain001SinglePaymentCorrectXmlString());
        pain001Document.getCstmrCdtTrfInitn()
                .getPmtInf().getFirst()
                .getCdtTrfTxInf()
                .getPmtId().setEndToEndId(null);

        // when + then
        assertThatThrownBy(() -> pain001PaymentMapper.mapFromRequestPain001Document(pain001Document))
                .isInstanceOf(MappingPain001ToDomainException.class)
                .hasMessageContaining("EndToEnd id is required");
    }


    private Document preparePain001Document(String pain001AsXmlString) throws SAXException, JAXBException {
        InputStream xmlInputStream = new ByteArrayInputStream(pain001AsXmlString.getBytes());
        URL xsdLimitedPain001SchemaUrl = getClass().getResource(LIMITED_PAIN001_XSD_PATH);
        assertThat(xsdLimitedPain001SchemaUrl).isNotNull();
        Schema limitedPain001Schema = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(xsdLimitedPain001SchemaUrl);
        JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        unmarshaller.setSchema(limitedPain001Schema);
        JAXBElement<Document> root = unmarshaller.unmarshal(new StreamSource(xmlInputStream), Document.class);
        return root.getValue();
    }

    private OffsetDateTime getOffsetDateTime(XMLGregorianCalendar creationDateTime) {
        if (creationDateTime == null) {
            throw new IllegalArgumentException("Creation date time is required");
        }
        if (creationDateTime.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new IllegalArgumentException("Creation date time must contain offset");
        }
        return creationDateTime.toGregorianCalendar()
                .toZonedDateTime()
                .toOffsetDateTime();
    }

}