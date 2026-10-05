package priv.home.paymentservice.data;

import org.springframework.stereotype.Component;
import priv.home.paymentservice.model.Payment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentStorage {

    private final Map<UUID, Payment> paymentStorage = new HashMap<>();


    public void addPaymentToStorage(Payment payment) {
        paymentStorage.put(payment.getId(), payment);
    }
}
