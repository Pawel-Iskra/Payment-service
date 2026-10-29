package priv.home.paymentservice.payment.iso20022.pain001;


import iso.std.iso._20022.tech.xsd.pain_001_001.*;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


@Component
public class Pain001PaymentMapper {

    private static final int NUMBER_OF_PAYMENT_INSTRUCTIONS_IS_ONE = 1;


    public Pain001PaymentData mapFromRequestPain001Document(Document pain001Document) {
        if (pain001Document == null) throw new MappingPain001ToDomainException("Pain.001 document must not be null");

        CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation = extractCustomerTransferInitiation(pain001Document);
        GroupHeader114 groupHeader = extractGroupHeader(ctrCrdtTrfInitiation);
        PaymentInstruction44 paymentInstrInfo = extractPaymentInstructionInfo(ctrCrdtTrfInitiation);
        CreditTransferTransaction61 crdtTrfTransaction = extractCreditTransferTransaction(paymentInstrInfo);
        return Pain001PaymentData.builder()
                .messageId(groupHeader.getMsgId())
                .creationDateTime(getOffsetDateTime(groupHeader.getCreDtTm()))
                .transactionCount(Integer.parseInt(groupHeader.getNbOfTxs()))
                .paymentInformationId(paymentInstrInfo.getPmtInfId())
                .endToEndId(extractEndToEndId(crdtTrfTransaction))
                .amount(extractAmount(crdtTrfTransaction))
                .currency(extractCurrency(crdtTrfTransaction))
                .debtorName(paymentInstrInfo.getDbtr().getNm())
                .debtorIban(extractDebtorIban(paymentInstrInfo))
                .debtorBic(getDebtorBic(paymentInstrInfo))
                .creditorName(crdtTrfTransaction.getCdtr().getNm())
                .creditorIban(extractCreditorIban(crdtTrfTransaction))
                .creditorBic(getCreditorBic(crdtTrfTransaction))
                .build();
    }


    private CustomerCreditTransferInitiationV12 extractCustomerTransferInitiation(Document pain001Document) {
        return Optional.ofNullable(pain001Document)
                .map(Document::getCstmrCdtTrfInitn)
                .orElseThrow(() -> new MappingPain001ToDomainException("Credit transfer transaction initiation is required"));
    }

    private GroupHeader114 extractGroupHeader(CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation) {
        return Optional.ofNullable(ctrCrdtTrfInitiation)
                .map(CustomerCreditTransferInitiationV12::getGrpHdr)
                .orElseThrow(() -> new MappingPain001ToDomainException("Group header is required"));
    }

    private PaymentInstruction44 extractPaymentInstructionInfo(CustomerCreditTransferInitiationV12 ctrCrdtTrfInitiation) {
        List<PaymentInstruction44> paymentInstructionList = ctrCrdtTrfInitiation.getPmtInf();
        if (paymentInstructionList == null || paymentInstructionList.isEmpty()) {
            throw new MappingPain001ToDomainException("Payment instruction is required");
        }
        if (paymentInstructionList.size() > NUMBER_OF_PAYMENT_INSTRUCTIONS_IS_ONE) {
            throw new MappingPain001ToDomainException("One and only one payment instruction is allowed");
        }
        return paymentInstructionList.getFirst();
    }

    private CreditTransferTransaction61 extractCreditTransferTransaction(PaymentInstruction44 paymentInstrInfo) {
        List<CreditTransferTransaction61> creditTransferTransactionList = paymentInstrInfo.getCdtTrfTxInf();

        if (creditTransferTransactionList == null || creditTransferTransactionList.isEmpty()) {
            throw new MappingPain001ToDomainException("Credit transfer transaction is required");
        }
        if (creditTransferTransactionList.size() > NUMBER_OF_PAYMENT_INSTRUCTIONS_IS_ONE) {
            throw new MappingPain001ToDomainException("One and only one credit transfer transaction is allowed");
        }
        return creditTransferTransactionList.getFirst();
    }

    private OffsetDateTime getOffsetDateTime(XMLGregorianCalendar creationDateTime) {
        if (creationDateTime == null) {
            throw new MappingPain001ToDomainException("Creation date time is required");
        }
        if (creationDateTime.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new MappingPain001ToDomainException("Creation date time must contain offset");
        }
        return creationDateTime.toGregorianCalendar()
                .toZonedDateTime()
                .toOffsetDateTime();
    }

    private String extractEndToEndId(CreditTransferTransaction61 crdtTrfTransaction) {
        return Optional.ofNullable(crdtTrfTransaction)
                .map(CreditTransferTransaction61::getPmtId)
                .map(PaymentIdentification6::getEndToEndId)
                .orElseThrow(() -> new MappingPain001ToDomainException("EndToEnd id is required"));
    }

    private BigDecimal extractAmount(CreditTransferTransaction61 crdtTrfTransaction) {
        return Optional.ofNullable(crdtTrfTransaction)
                .map(CreditTransferTransaction61::getAmt)
                .map(AmountType4Choice::getInstdAmt)
                .map(ActiveOrHistoricCurrencyAndAmount::getValue)
                .orElseThrow(() -> new MappingPain001ToDomainException("Amount is required"));
    }

    private String extractCurrency(CreditTransferTransaction61 crdtTrfTransaction) {
        return Optional.ofNullable(crdtTrfTransaction)
                .map(CreditTransferTransaction61::getAmt)
                .map(AmountType4Choice::getInstdAmt)
                .map(ActiveOrHistoricCurrencyAndAmount::getCcy)
                .orElseThrow(() -> new MappingPain001ToDomainException("Currency is required"));
    }

    private String extractDebtorIban(PaymentInstruction44 paymentInstrInfo) {
        return Optional.ofNullable(paymentInstrInfo.getDbtrAcct())
                .map(CashAccount40::getId)
                .map(AccountIdentification4Choice::getIBAN)
                .orElseThrow(() -> new MappingPain001ToDomainException("Debtor IBAN is required"));
    }

    private String getDebtorBic(PaymentInstruction44 paymentInstrInfo) {
        return Optional.ofNullable(paymentInstrInfo)
                .map(PaymentInstruction44::getDbtrAgt)
                .map(BranchAndFinancialInstitutionIdentification8::getFinInstnId)
                .map(FinancialInstitutionIdentification23::getBICFI)
                .orElseThrow(() -> new MappingPain001ToDomainException("Debtor BICFI is required"));
    }

    private String extractCreditorIban(CreditTransferTransaction61 crdtTrfTransaction) {
        return Optional.ofNullable(crdtTrfTransaction.getCdtrAcct())
                .map(CashAccount40::getId)
                .map(AccountIdentification4Choice::getIBAN)
                .orElseThrow(() -> new MappingPain001ToDomainException("Creditor IBAN is required"));
    }

    private String getCreditorBic(CreditTransferTransaction61 crdtTrfTransaction) {
        return Optional.ofNullable(crdtTrfTransaction)
                .map(CreditTransferTransaction61::getCdtrAgt)
                .map(BranchAndFinancialInstitutionIdentification8::getFinInstnId)
                .map(FinancialInstitutionIdentification23::getBICFI)
                .orElseThrow(() -> new MappingPain001ToDomainException("Creditor BICFI is required"));
    }
}
