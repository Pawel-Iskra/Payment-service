package priv.home.paymentservice.payment.api.errorhandling;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;
import priv.home.paymentservice.payment.iso20022.pain001.InvalidPain001XmlException;
import priv.home.paymentservice.payment.iso20022.pain001.MappingPain001ToDomainException;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001XmlErrorMessageExtractor;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Slf4j
@ControllerAdvice
@AllArgsConstructor
public class ControllerExceptionHandler {

    private static final String UNEXPECTED_ERROR_OCCURRED = "Unexpected error occurred";
    private static final String IDEMPOTENCY_KEY_IS_REQUIRED = "Required header 'idempotency-key' is not present";
    private static final String UNRECOGNIZED_PROPERTY = "Unrecognized property";
    private static final String INTERNAL_SERVER_ERROR = "Internal server error (500). ";
    private static final String INVALID_JSON_REQUEST = "Invalid JSON request";
    private static final String CURRENCY_MUST_RESPECT_PATTERN = "Currency code must consist of exactly 3 uppercase letters";
    private static final String CURRENCY_IS_REQUIRED = "Currency is required";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Amount must be greater than 0";
    private static final String AMOUNT_MUST_NOT_EXCEED = "Amount must not exceed 9999999999.99 and must have at most 2 decimal places";

    private final Pain001XmlErrorMessageExtractor pain001XmlErrorMessageExtractor;


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
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, IDEMPOTENCY_KEY_IS_REQUIRED));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> constraintValidationException(HandlerMethodValidationException exception) {
        String responseMessage = getMethodViolationMessageResponse(exception);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, responseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> notValidRequest(MethodArgumentNotValidException exception) {
        String errorResponseMessage = getErrorResponseMessageFromNotValidJsonRequest(exception);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, errorResponseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> requestNotReadable(HttpMessageNotReadableException exception) {
        String errorResponseMessage = getErrorResponseMessageFromNotReadableJsonRequest(exception);
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
        String errorMessage = pain001XmlErrorMessageExtractor.extractMessageFromPain001XmlParseException(exception);
        // TODO: responseMessages for incorrect amount/currency must be sam for Json and Xml - to be upgraded
        if (errorMessage.contains("minExclusive")) {
            errorMessage = AMOUNT_MUST_BE_GREATER_THAN_ZERO;
        }
        if (errorMessage.contains("fractionDigits") || errorMessage.contains("fraction digits") || errorMessage.contains("maxInclusive")) {
            errorMessage = AMOUNT_MUST_NOT_EXCEED;
        }
        if (errorMessage.contains("[A-Z]{3,3}")) {
            errorMessage = CURRENCY_MUST_RESPECT_PATTERN;
        }
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, errorMessage));
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
        String responseMessage = exception.getBody() != null ? exception.getBody().getDetail() : exception.getMessage();
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, responseMessage));
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
        String responseMessage = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST, responseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> mediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        String responseMessage = exception.getBody() != null ? exception.getBody().getDetail() : exception.getMessage();
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.NOT_ACCEPTABLE, responseMessage));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorResponse> technicalException(Exception exception) {
        log.error(INTERNAL_SERVER_ERROR, exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR_OCCURRED));
    }


    private String getErrorResponseMessageFromNotValidJsonRequest(MethodArgumentNotValidException exception) {
        String errorResponseMessage;
        List<String> errorMessages = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();
        if (errorMessages.isEmpty()) {
            errorResponseMessage = exception.getMessage();
        } else {
            errorResponseMessage = errorMessages
                    .toString()
                    .replaceAll("[\\[\\]]", "");
        }
        return errorResponseMessage;
    }

    private String getErrorResponseMessageFromNotReadableJsonRequest(HttpMessageNotReadableException exception) {
        String exceptionMessage = exception.getMessage();
        if (exceptionMessage == null) {
            return INVALID_JSON_REQUEST;
        }
        if (!exceptionMessage.contains(UNRECOGNIZED_PROPERTY)) {
            return exceptionMessage;
        }
        int startMessage = exceptionMessage.indexOf(UNRECOGNIZED_PROPERTY);
        int endMessage = exceptionMessage.indexOf("(");
        if (endMessage == -1) {
            return exceptionMessage;
        }
        return exceptionMessage
                .substring(startMessage, endMessage)
                .replace("\"", "'");
    }

    private String getMethodViolationMessageResponse(HandlerMethodValidationException exception) {
        return exception.getParameterValidationResults().stream()
                .flatMap(validationResult -> validationResult.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
    }
}
