package priv.home.paymentservice.payment.iso20022.pain001;


import iso.std.iso._20022.tech.xsd.pain_001_001.*;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.OffsetDateTime;
import java.util.Optional;


@Component
public class Pain001PaymentMapper {


    public Pain001PaymentData mapFromRequestPain001Document(Document pain001Document) {
        if (pain001Document == null) throw new IllegalArgumentException("Pain.001 document must not be null");

        CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation = extractCustomerTransferInitiation(pain001Document);
        GroupHeader114 groupHeader = extractGroupHeader(ctrCrdtTrfInitiation);
        PaymentInstruction44 paymentInstrInfo = extractPaymentInstructionInfo(ctrCrdtTrfInitiation);
        CreditTransferTransaction61 crdtTrfTransaction = extractCreditTransferTransaction(paymentInstrInfo);
        return Pain001PaymentData.builder()
                .messageId(groupHeader.getMsgId())
                .creationDateTime(getOffsetDateTime(groupHeader.getCreDtTm()))
                .transactionCount(Integer.parseInt(groupHeader.getNbOfTxs()))
                .paymentInformationId(paymentInstrInfo.getPmtInfId())
                .endToEndId(crdtTrfTransaction.getPmtId().getEndToEndId())
                .amount(crdtTrfTransaction.getAmt().getInstdAmt().getValue())
                .currency(crdtTrfTransaction.getAmt().getInstdAmt().getCcy())
                .debtorName(paymentInstrInfo.getDbtr().getNm())
                .debtorIban(paymentInstrInfo.getDbtrAcct().getId().getIBAN())
                .debtorBic(getBic(paymentInstrInfo.getDbtrAgt()))
                .creditorName(crdtTrfTransaction.getCdtr().getNm())
                .creditorIban(crdtTrfTransaction.getCdtrAcct().getId().getIBAN())
                .creditorBic(getBic(crdtTrfTransaction.getCdtrAgt()))
                .build();
    }


    private CustomerCreditTransferInitiationV12 extractCustomerTransferInitiation(Document pain001Document) {
        return Optional.ofNullable(pain001Document)
                .map(Document::getCstmrCdtTrfInitn)
                .orElseThrow(() -> new IllegalArgumentException("Credit transfer transaction initiation is required"));
    } // TODO: maybe custom MappingToDomaiException

    private GroupHeader114 extractGroupHeader(CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation) {
        return Optional.ofNullable(ctrCrdtTrfInitiation)
                .map(CustomerCreditTransferInitiationV12::getGrpHdr)
                .orElseThrow(() -> new IllegalArgumentException("Group header is required"));
    }

    private PaymentInstruction44 extractPaymentInstructionInfo(CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation) {
        //TODO: check that must be one element list
        return Optional.ofNullable(ctrCrdtTrfInitiation)
                .map(CustomerCreditTransferInitiationV12::getPmtInf)
                .map(list -> list.isEmpty() ? null : list.getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Payment instruction information is required"));
    }

    private CreditTransferTransaction61 extractCreditTransferTransaction(PaymentInstruction44 paymentInstrInfo) {
        //TODO: check that must be one element list
        return Optional.ofNullable(paymentInstrInfo)
                .map(PaymentInstruction44::getCdtTrfTxInf)
                .map(list -> list.isEmpty() ? null : list.getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Credit transfer transaction is required"));
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

    private String getBic(BranchAndFinancialInstitutionIdentification8 debtorAgent) {
        if (debtorAgent == null || debtorAgent.getFinInstnId() == null) {
            return null;
        }
        return debtorAgent.getFinInstnId().getBICFI();
    }


}
