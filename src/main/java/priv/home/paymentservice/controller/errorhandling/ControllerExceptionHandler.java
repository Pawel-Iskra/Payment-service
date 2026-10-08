package priv.home.paymentservice.controller.errorhandling;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import priv.home.paymentservice.exceptions.SinglePaymentNotFoundException;


@ControllerAdvice
public class ControllerExceptionHandler {

    private static final String UNEXPECTED_ERROR_OCCURRED = "Unexpected error occurred";
    private static final String HEADER_IDEMPOTENCY_KEY_NOT_PRESENT = "Required header 'idempotency-key' is not present";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Field 'amount' must be greater than 0";
    private static final String CURRENCY_MUST_NOT_BE_BLANK = "Field 'currency' must not be blank";
    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";


    @ExceptionHandler
    public ResponseEntity<ErrorResponse> singlePaymentNotFound(SinglePaymentNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> missingRequestHeader(MissingRequestHeaderException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, HEADER_IDEMPOTENCY_KEY_NOT_PRESENT));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> notValidRequest(MethodArgumentNotValidException exception) {
        String exceptionMessage = exception.getMessage();
        String errorResponseMessage;
        if (exceptionMessage.contains(AMOUNT) && exceptionMessage.contains(CURRENCY)) {
            errorResponseMessage = AMOUNT_MUST_BE_GREATER_THAN_ZERO + ", " + CURRENCY_MUST_NOT_BE_BLANK;
        } else if (exceptionMessage.contains(AMOUNT)) {
            errorResponseMessage = AMOUNT_MUST_BE_GREATER_THAN_ZERO;
        } else {
            errorResponseMessage = CURRENCY_MUST_NOT_BE_BLANK;
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, errorResponseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> technicalException(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_OCCURRED));
    }

}
