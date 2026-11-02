package priv.home.paymentservice.payment.api.errorhandling;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
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
    private static final String CURRENCY_MUST_MATCH_PATTERN = "Field 'currency' must match the pattern [A-Z]{3}";
    private static final String UNRECOGNIZED_PROPERTY = "Unrecognized property";
    private static final String INTERNAL_SERVER_ERROR = "Internal server error (500). ";
    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";


    @ExceptionHandler
    public ResponseEntity<ErrorResponse> singlePaymentNotFound(SinglePaymentNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> missingRequestHeader(MissingRequestHeaderException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, HEADER_IDEMPOTENCY_KEY_NOT_PRESENT));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> notValidRequest(MethodArgumentNotValidException exception) {
        String errorResponseMessage = getErrorResponseMessageForInvalidJsonRequest(exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, errorResponseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestNotReadable(HttpMessageNotReadableException exception) {
        String errorResponseMessage = getErrorResponseMessageForInvalidJsonRequest(exception.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, errorResponseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> mappingPain001ToDomainException(MappingPain001ToDomainException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> invalidPain001xml(InvalidPain001XmlException exception) {
        // TODO: more details for different cases
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> noResourceFound(NoResourceFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> unsupportedMediatype(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> methodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, exception.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> argumentMismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getCause().getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> mediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.NOT_ACCEPTABLE, exception.getBody().getDetail()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> technicalException(Exception exception) {
        log.error(INTERNAL_SERVER_ERROR, exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_OCCURRED));
    }


    private String getErrorResponseMessageForInvalidJsonRequest(String exceptionMessage) {
        String errorResponseMessage;
        if (exceptionMessage.contains(AMOUNT) && exceptionMessage.contains(CURRENCY)) {
            errorResponseMessage = AMOUNT_MUST_BE_GREATER_THAN_ZERO + ", " + CURRENCY_MUST_MATCH_PATTERN;
        } else if (exceptionMessage.contains(AMOUNT) || exceptionMessage.contains("java.math.BigDecimal")) {
            errorResponseMessage = AMOUNT_MUST_BE_GREATER_THAN_ZERO;
        } else if (exceptionMessage.contains(CURRENCY)) {
            errorResponseMessage = CURRENCY_MUST_MATCH_PATTERN;
        } else if (exceptionMessage.contains(UNRECOGNIZED_PROPERTY) && exceptionMessage.contains("(")) {
            errorResponseMessage = UNRECOGNIZED_PROPERTY + exceptionMessage.substring(
                    exceptionMessage.indexOf(UNRECOGNIZED_PROPERTY) + UNRECOGNIZED_PROPERTY.length(),
                    exceptionMessage.indexOf("("));
        } else {
            errorResponseMessage = exceptionMessage;
        }
        return errorResponseMessage;
    }
}
