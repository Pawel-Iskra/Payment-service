package priv.home.paymentservice.payment.iso20022.pain001;


import iso.std.iso._20022.tech.xsd.pain_001_001.*;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.OffsetDateTime;
import java.util.Optional;

public class Pain001PaymentMapper {


    public Pain001PaymentData mapFromRequest(Document pain001Document) {
        if (pain001Document == null) throw new IllegalArgumentException("Pain.001 document must not be null");

        CustomerCreditTransferInitiationV09 ctrCrdtTrfInitiation = extractCustomerTransferInitiation(pain001Document);
        GroupHeader85 groupHeader = extractGroupHeader(ctrCrdtTrfInitiation);
        PaymentInstruction30 paymentInstrInfo = extractPaymentInstructionInfo(ctrCrdtTrfInitiation);
        CreditTransferTransaction34 crdtTrfTransaction = extractCreditTransferTransaction(paymentInstrInfo);
        
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


    private CustomerCreditTransferInitiationV09 extractCustomerTransferInitiation(Document pain001Document) {
        return Optional.ofNullable(pain001Document)
                .map(Document::getCstmrCdtTrfInitn)
                .orElseThrow(() -> new IllegalArgumentException("Credit transfer transaction initiation is required"));
    } // TODO: maybe custom MappingToDomaiException

    private GroupHeader85 extractGroupHeader(CustomerCreditTransferInitiationV09 ctrCrdtTrfInitiation) {
        return Optional.ofNullable(ctrCrdtTrfInitiation)
                .map(CustomerCreditTransferInitiationV09::getGrpHdr)
                .orElseThrow(() -> new IllegalArgumentException("Group header is required"));
    }

    private PaymentInstruction30 extractPaymentInstructionInfo(CustomerCreditTransferInitiationV09 ctrCrdtTrfInitiation) {
        //TODO: check that must be one element list
        return Optional.ofNullable(ctrCrdtTrfInitiation)
                .map(CustomerCreditTransferInitiationV09::getPmtInf)
                .map(list -> list.isEmpty() ? null : list.getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Payment instruction information is required"));
    }

    private CreditTransferTransaction34 extractCreditTransferTransaction(PaymentInstruction30 paymentInstrInfo) {
        //TODO: check that must be one element list
        return Optional.ofNullable(paymentInstrInfo)
                .map(PaymentInstruction30::getCdtTrfTxInf)
                .map(list -> list.isEmpty() ? null : list.getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Credit transfer transaction is required"));
    }

    private OffsetDateTime getOffsetDateTime(XMLGregorianCalendar creDtTm) {
        if (creDtTm == null) {
            throw new IllegalArgumentException("Creation date time is required");
        }
        if (creDtTm.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new IllegalArgumentException("Creation date time must contain offset");
        }
        return creDtTm.toGregorianCalendar()
                .toZonedDateTime()
                .toOffsetDateTime();
    }

    private String getBic(BranchAndFinancialInstitutionIdentification6 dbtrAgt) {
        if (dbtrAgt == null || dbtrAgt.getFinInstnId() == null) {
            return null;
        }
        return dbtrAgt.getFinInstnId().getBICFI();
    }


}
