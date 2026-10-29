package priv.home.paymentservice.payment.iso20022.pain001;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MappingPain001ToDomainException extends RuntimeException {

    private final String message;

    @Override
    public String getMessage() {
        return this.message;
    }
}
