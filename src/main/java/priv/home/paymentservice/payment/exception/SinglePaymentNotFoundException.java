package priv.home.paymentservice.payment.exception;

import java.util.UUID;

public class SinglePaymentNotFoundException extends RuntimeException {

    private final UUID paymentId;

    public SinglePaymentNotFoundException(UUID paymentId) {
        this.paymentId = paymentId;
    }

    @Override
    public String getMessage() {
        return "Not found payment with given id = " + paymentId;
    }
}
