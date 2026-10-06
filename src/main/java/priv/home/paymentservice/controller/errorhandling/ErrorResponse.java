package priv.home.paymentservice.controller.errorhandling;

import org.springframework.http.HttpStatus;

public record ErrorResponse(HttpStatus httpStatus, String message) {
}
