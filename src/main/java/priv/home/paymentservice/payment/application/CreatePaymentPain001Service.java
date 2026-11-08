package priv.home.paymentservice.payment.application;


import iso.std.iso._20022.tech.xsd.pain_001_001.Document;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.CreditorRequest;
import priv.home.paymentservice.payment.api.dto.DebtorRequest;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001PaymentData;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001PaymentMapper;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001XmlParser;
import priv.home.paymentservice.payment.iso20022.pain002.Pain002ResponseMapper;

import java.io.ByteArrayInputStream;

@Service
@AllArgsConstructor
public class CreatePaymentPain001Service {

    private final Pain001XmlParser pain001XmlParser;
    private final Pain001PaymentMapper pain001PaymentMapper;
    private final PaymentService paymentService;
    private final Pain002ResponseMapper pain002ResponseMapper;


    public CreatePain001Result createPaymentFromPain001Xml(String idempotencyKey, byte[] xmlPain001Bytes) {
        Document pain001Document = pain001XmlParser.parse(new ByteArrayInputStream(xmlPain001Bytes));
        Pain001PaymentData pain001PaymentData = pain001PaymentMapper.mapFromRequestPain001Document(pain001Document);
        CreatePaymentResult createPaymentResult = paymentService
                .createPayment(idempotencyKey, getCreatePaymentRequest(pain001PaymentData));

        return CreatePain001Result.builder()
                .paymentId(createPaymentResult.paymentId())
                .wasAlreadyInDb(createPaymentResult.wasAlreadyInDb())
                .createPain001PaymentStatusReportDocument(pain002ResponseMapper
                        .buildCustomerPaymentStatusReport(pain001PaymentData))
                .build();
    }


    private CreatePaymentRequest getCreatePaymentRequest(Pain001PaymentData pain001PaymentData) {
        return CreatePaymentRequest.builder()
                .amount(pain001PaymentData.amount())
                .currency(pain001PaymentData.currency())
                .endToEndId(pain001PaymentData.endToEndId())
                .debtorRequest(new DebtorRequest(pain001PaymentData.debtorName(), pain001PaymentData.debtorIban()))
                .creditorRequest(new CreditorRequest(pain001PaymentData.creditorName(), pain001PaymentData.creditorIban()))
                .build();
    }

}
