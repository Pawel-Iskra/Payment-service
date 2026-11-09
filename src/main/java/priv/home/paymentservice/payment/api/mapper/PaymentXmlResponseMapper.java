package priv.home.paymentservice.payment.api.mapper;

import iso.std.iso._20022.tech.xsd.pain_002_001.Document;
import iso.std.iso._20022.tech.xsd.pain_002_001.ObjectFactory;
import jakarta.xml.bind.JAXBElement;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.payment.application.CreatePain001Result;

@Component
public class PaymentXmlResponseMapper {

    public JAXBElement<Document> buildXmlResponse(CreatePain001Result createPain001Result) {
        return new ObjectFactory().createDocument(
                createPain001Result.createPain001PaymentStatusReportDocument());
    }

}
