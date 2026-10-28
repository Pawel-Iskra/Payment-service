package priv.home.paymentservice.payment.application;

import iso.std.iso._20022.tech.xsd.pain_002_001.Document;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreatePain001Result(
        UUID paymentId,
        boolean wasAlreadyInDb,
        Document createPaymentStatusReportDocument) {
}
