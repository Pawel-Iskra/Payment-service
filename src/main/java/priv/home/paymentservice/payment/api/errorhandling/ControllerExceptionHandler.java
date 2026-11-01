package priv.home.paymentservice.payment.api.errorhandling;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;
import priv.home.paymentservice.payment.iso20022.pain001.InvalidPain001XmlException;
import priv.home.paymentservice.payment.iso20022.pain001.MappingPain001ToDomainException;


@Slf4j
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

    // TODO:tests
    @ExceptionHandler
    public ResponseEntity<ErrorResponse> mappingPain001ToDomainException(MappingPain001ToDomainException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> invalidPain001xml(InvalidPain001XmlException exception) {
        log.error(exception.getMessage() + ",\n" + exception.getCause());
        // TODO: more details for different cases
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> noResourceFound(NoResourceFoundException exception) {
        log.error(exception.getMessage() + ",\n" + exception.getCause());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> unsupportedMediatype(HttpMediaTypeNotSupportedException exception) {
        log.error(exception.getMessage() + ",\n" + exception.getCause());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.error(exception.getMessage() + ",\n" + exception.getCause());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> argumentMismatch(MethodArgumentTypeMismatchException exception) {
        log.error(exception.getMessage() + ",\n" + exception.getCause());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getCause().getMessage()));
    }

    // TODO: HTTP.406 maybe?

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> technicalException(Exception exception) {
        log.error(exception.getMessage() + ",\n" + exception.getCause());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_OCCURRED));
    }

}
