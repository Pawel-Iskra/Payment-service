package priv.home.paymentservice.data;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.model.Payment;

import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@Component
public class PaymentStorage {

    private Map<UUID, Payment> paymentStorage;


    public void addPaymentToStorage(Payment payment) {
        paymentStorage.put(payment.getId(), payment);
    }
}
