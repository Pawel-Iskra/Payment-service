package priv.home.paymentservice.payment.api.errorhandling;

import org.springframework.http.HttpStatus;

public record ErrorResponse(HttpStatus httpStatus, String message) {
}
