package priv.home.paymentservice.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import priv.home.paymentservice.data.PaymentStorage;
import priv.home.paymentservice.dto.PaymentDto;
import priv.home.paymentservice.model.Payment;
import priv.home.paymentservice.model.PaymentResponse;
import priv.home.paymentservice.model.PaymentStatus;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class PaymentService {

    private final PaymentStorage paymentStorage;


    public Optional<PaymentResponse> createPayment(PaymentDto paymentDto) {
        Payment payment = buildPaymentFromDto(paymentDto);
        if (paymentStorage.addPaymentToStorage(payment)) {
            return Optional.ofNullable(buildPaymentResponse(payment));
        } else {
            return Optional.empty();
        }
    }


    private PaymentResponse buildPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentStatus(payment.getPaymentStatus())
                .build();
    }


    private Payment buildPaymentFromDto(PaymentDto paymentDto) {
        return Payment.builder()
                .id(generateUuid())
                .amount(paymentDto.getAmount())
                .currency(paymentDto.getCurrency())
                .paymentStatus(PaymentStatus.CREATED)
                .build();
    }


    private UUID generateUuid() {
        return UUID.randomUUID();
    }
}
