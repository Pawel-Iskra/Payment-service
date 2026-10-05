package priv.home.paymentservice.data;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import priv.home.paymentservice.model.Payment;

import java.util.Set;

@AllArgsConstructor
@Component
public class PaymentStorage {

    private Set<Payment> paymentStorage;


    public boolean addPaymentToStorage(Payment payment) {
        return paymentStorage.add(payment);
    }
}
