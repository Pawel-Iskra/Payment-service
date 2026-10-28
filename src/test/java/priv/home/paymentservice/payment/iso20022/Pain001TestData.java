package priv.home.paymentservice.payment.iso20022;

public class Pain001TestData {

    // aligned to limited-pain001.xsd
    private static final String PAIN001_SINGLE_PAYMENT_CORRECT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.12">
                <CstmrCdtTrfInitn>
                    <GrpHdr>
                        <MsgId>MSG-20261027-001</MsgId>
                        <CreDtTm>2026-10-27T10:00:00</CreDtTm>
                        <NbOfTxs>1</NbOfTxs>
                        <CtrlSum>125.50</CtrlSum>
                        <InitgPty><Nm>Example Debtor Ltd</Nm></InitgPty>
                    </GrpHdr>
                    <PmtInf>
                        <PmtInfId>PMT-INF-001</PmtInfId>
                        <PmtMtd>TRF</PmtMtd>
                        <NbOfTxs>1</NbOfTxs>
                        <CtrlSum>125.50</CtrlSum>
                        <PmtTpInf><SvcLvl><Cd>SEPA</Cd></SvcLvl></PmtTpInf>
                        <ReqdExctnDt><Dt>2026-10-28</Dt></ReqdExctnDt>
                        <Dbtr><Nm>Example Debtor Ltd</Nm></Dbtr>
                        <DbtrAcct><Id><IBAN>DE89370400440532013000</IBAN></Id></DbtrAcct>
                        <DbtrAgt><FinInstnId><BICFI>COBADEFFXXX</BICFI></FinInstnId></DbtrAgt>
                        <ChrgBr>SLEV</ChrgBr>
                        <CdtTrfTxInf>
                            <PmtId><EndToEndId>E2E-20261027-0001</EndToEndId></PmtId>
                            <Amt><InstdAmt Ccy="EUR">125.50</InstdAmt></Amt>
                            <CdtrAgt><FinInstnId><BICFI>DEUTDEFFXXX</BICFI></FinInstnId></CdtrAgt>
                            <Cdtr><Nm>Example Creditor GmbH</Nm></Cdtr>
                            <CdtrAcct><Id><IBAN>DE44500105175407324931</IBAN></Id></CdtrAcct>
                        </CdtTrfTxInf>
                    </PmtInf>
                </CstmrCdtTrfInitn>
            </Document>
            """;

    private static final String PAIN001_TWO_TRANSACTION_BLOCKS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.12">
                <CstmrCdtTrfInitn>
                    <GrpHdr>
                        <MsgId>MSG-20261027-001</MsgId>
                        <CreDtTm>2026-10-27T12:00:00</CreDtTm>
                        <NbOfTxs>2</NbOfTxs>
                        <CtrlSum>371.00</CtrlSum>
                        <InitgPty><Nm>Example Debtor Ltd</Nm></InitgPty>
                    </GrpHdr>
                    <PmtInf>
                        <PmtInfId>PMT-INF-001</PmtInfId>
                        <PmtMtd>TRF</PmtMtd>
                        <NbOfTxs>2</NbOfTxs>
                        <CtrlSum>371.00</CtrlSum>
                        <PmtTpInf><SvcLvl><Cd>SEPA</Cd></SvcLvl></PmtTpInf>
                        <ReqdExctnDt><Dt>2026-10-28</Dt></ReqdExctnDt>
                        <Dbtr><Nm>Example Debtor Ltd</Nm></Dbtr>
                        <DbtrAcct><Id><IBAN>DE89370400440532013000</IBAN></Id></DbtrAcct>
                        <DbtrAgt><FinInstnId><BICFI>COBADEFFXXX</BICFI></FinInstnId></DbtrAgt>
                        <ChrgBr>SLEV</ChrgBr>
                        <CdtTrfTxInf>
                            <PmtId><EndToEndId>E2E-20261027-0001</EndToEndId></PmtId>
                            <Amt><InstdAmt Ccy="EUR">125.50</InstdAmt></Amt>
                            <CdtrAgt><FinInstnId><BICFI>DEUTDEFFXXX</BICFI></FinInstnId></CdtrAgt>
                            <Cdtr><Nm>Example Creditor GmbH</Nm></Cdtr>
                            <CdtrAcct><Id><IBAN>DE44500105175407324931</IBAN></Id></CdtrAcct>
                        </CdtTrfTxInf>
                        <CdtTrfTxInf>
                             <PmtId><EndToEndId>E2E-20261027-0002</EndToEndId></PmtId>
                             <Amt><InstdAmt Ccy="EUR">245.50</InstdAmt></Amt>
                             <CdtrAgt><FinInstnId><BICFI>DEUTDEFFXXX</BICFI></FinInstnId></CdtrAgt>
                             <Cdtr><Nm>Example Creditor GmbH</Nm></Cdtr>
                             <CdtrAcct><Id><IBAN>DE44500105175407324931</IBAN></Id></CdtrAcct>
                        </CdtTrfTxInf>
                    </PmtInf>
                </CstmrCdtTrfInitn>
            </Document>
            """;


    public static String getPain001SinglePaymentCorrectXmlString() {
        return PAIN001_SINGLE_PAYMENT_CORRECT_XML;
    }

    public static String getPain001SinglePaymentNotCorrectStructureXmlString() {
        return PAIN001_SINGLE_PAYMENT_CORRECT_XML.replace("<CstmrCdtTrfInitn>", "");
    }

    public static String getPain001SinglePaymentWithTransactionCountAsTwoXmlString() {
        return PAIN001_SINGLE_PAYMENT_CORRECT_XML.replace("<NbOfTxs>1</NbOfTxs>", "<NbOfTxs>2</NbOfTxs>");
    }

    public static String getPain001SinglePaymentWithTwoTransactionBlocksXmlString() {
        return PAIN001_TWO_TRANSACTION_BLOCKS.replace("<NbOfTxs>2</NbOfTxs>", "<NbOfTxs>1</NbOfTxs>");
    }
}
