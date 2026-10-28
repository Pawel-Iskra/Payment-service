package priv.home.paymentservice.payment.iso20022.pain001;

public class InvalidPain001XmlException extends RuntimeException {


    public InvalidPain001XmlException(String message) {
        super(message);
    }

    public InvalidPain001XmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
