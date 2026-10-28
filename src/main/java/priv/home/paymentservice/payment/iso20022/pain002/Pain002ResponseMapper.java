package priv.home.paymentservice.payment.iso20022.pain002;

import iso.std.iso._20022.tech.xsd.pain_002_001.*;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001PaymentData;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class Pain002ResponseMapper {

    private static final String STATUS_PREFIX = "STATUS-";
    private static final String PAIN001_VERSION = "pain.001.001.12";
    private static final String TRANSACTION_STATUS_REPORT = "ACTC";


    // TODO: or public static and no component
    public Document buildCustomerPaymentStatusReport(Pain001PaymentData pain001PaymentData) {
        Document statusReportDocument = new Document();
        CustomerPaymentStatusReportV14 customerPaymentStatusReportV14 = new CustomerPaymentStatusReportV14();
        GroupHeader128 groupHeader = new GroupHeader128();
        groupHeader.setMsgId(STATUS_PREFIX + generateUuid());
        groupHeader.setCreDtTm(toXmlGeorgianCalendar(OffsetDateTime.now()));

        OriginalGroupHeader22 groupHeaderAndStatus = new OriginalGroupHeader22();
        groupHeaderAndStatus.setOrgnlMsgId(pain001PaymentData.messageId());
        groupHeaderAndStatus.setOrgnlMsgNmId(PAIN001_VERSION);

        OriginalPaymentInstruction51 paymentInstructionAndStatus = new OriginalPaymentInstruction51();
        paymentInstructionAndStatus.setOrgnlPmtInfId(pain001PaymentData.paymentInformationId());
        PaymentTransaction160 paymentTransaction = new PaymentTransaction160();
        paymentTransaction.setOrgnlEndToEndId(pain001PaymentData.endToEndId());
        paymentTransaction.setTxSts(TRANSACTION_STATUS_REPORT);
        paymentInstructionAndStatus.getTxInfAndSts().add(paymentTransaction);

        customerPaymentStatusReportV14.getOrgnlPmtInfAndSts().clear();
        customerPaymentStatusReportV14.getOrgnlPmtInfAndSts().add(paymentInstructionAndStatus);
        customerPaymentStatusReportV14.setOrgnlGrpInfAndSts(groupHeaderAndStatus);
        customerPaymentStatusReportV14.setGrpHdr(groupHeader);
        statusReportDocument.setCstmrPmtStsRpt(customerPaymentStatusReportV14);
        return statusReportDocument;
    }


    private UUID generateUuid() {
        return UUID.randomUUID();
    }

    private XMLGregorianCalendar toXmlGeorgianCalendar(OffsetDateTime offsetDateTime) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(
                    offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (DatatypeConfigurationException exception) {
            throw new IllegalStateException("Cannot createXML date-time value", exception);
        }
    }
}
