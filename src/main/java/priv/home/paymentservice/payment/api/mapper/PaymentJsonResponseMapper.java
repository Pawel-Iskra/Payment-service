package priv.home.paymentservice.payment.api.mapper;

import org.springframework.stereotype.Component;
import priv.home.paymentservice.payment.api.dto.PaymentResponse;
import priv.home.paymentservice.payment.application.CreatePaymentResult;
import priv.home.paymentservice.payment.application.PaymentRetrieveResult;

@Component
public class PaymentJsonResponseMapper {

    public PaymentResponse buildPaymentJsonResponse(CreatePaymentResult createPaymentResult) {
        return PaymentResponse.builder()
                .paymentId(createPaymentResult.paymentId())
                .endToEndId(createPaymentResult.endToEndId())
                .debtorIban(createPaymentResult.debtorIban())
                .creditorIban(createPaymentResult.creditorIban())
                .amount(createPaymentResult.amount())
                .currency(createPaymentResult.currency())
                .paymentStatus(createPaymentResult.paymentStatus())
                .build();
    }

    public PaymentResponse buildPaymentJsonResponse(PaymentRetrieveResult paymentRetrieveResult) {
        return PaymentResponse.builder()
                .paymentId(paymentRetrieveResult.paymentId())
                .endToEndId(paymentRetrieveResult.endToEndId())
                .debtorIban(paymentRetrieveResult.debtorIban())
                .creditorIban(paymentRetrieveResult.creditorIban())
                .amount(paymentRetrieveResult.amount())
                .currency(paymentRetrieveResult.currency())
                .paymentStatus(paymentRetrieveResult.paymentStatus())
                .build();
    }
}
