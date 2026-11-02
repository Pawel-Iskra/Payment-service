package priv.home.paymentservice.payment.api.errorhandling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ControllerExceptionHandlerTest {

    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String UNEXPECTED_ERROR_OCCURRED = "Unexpected error occurred";
    private static final String HEADER_IDEMPOTENCY_KEY_NOT_PRESENT = "Required header 'idempotency-key' is not present";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Field 'amount' must be greater than 0";
    private static final String CURRENCY_MUST_MATCH_PATTERN = "Field 'currency' must match the pattern [A-Z]{3}";
    private static final String UNRECOGNIZED_PROPERTY = "Unrecognized property";
    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";

    private final ControllerExceptionHandler exceptionHandler = new ControllerExceptionHandler();


    @Test
    public void shouldReturnNotFoundWhenPaymentDoesNotExist() {
        // given
        UUID paymentId = generateRandomUuid();
        SinglePaymentNotFoundException singlePaymentNotFoundException = new SinglePaymentNotFoundException(paymentId);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler
                .singlePaymentNotFound(singlePaymentNotFoundException);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseResponseEntity.getBody().message()).isEqualTo(singlePaymentNotFoundException.getMessage());
    }

    @Test
    public void shouldReturnBadRequestWhenMissingRequestHeader() throws NoSuchMethodException {
        // given
        MissingRequestHeaderException missingHeaderException = new MissingRequestHeaderException(
                IDEMPOTENCY_KEY,
                getTestMethodParameter());

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler
                .missingRequestHeader(missingHeaderException);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).isEqualTo(HEADER_IDEMPOTENCY_KEY_NOT_PRESENT);
    }

    @Test
    public void shouldReturnBadRequestWhenRequestAmountIsZero() throws NoSuchMethodException {
        // given
        CreatePaymentRequest notValidPaymentRequest = new CreatePaymentRequest(new BigDecimal("0"), "PLN");
        DirectFieldBindingResult bindingResult = new DirectFieldBindingResult(notValidPaymentRequest, "request");
        bindingResult.rejectValue(
                "amount",
                "Positive",
                "Amount must be positive");
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                getTestMethodParameter(),
                bindingResult);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler
                .notValidRequest(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).isEqualTo(AMOUNT_MUST_BE_GREATER_THAN_ZERO);
    }

    @Test
    public void shouldReturnBadRequestWhenRequestNotReadable() throws NoSuchMethodException {
        // given
        HttpInputMessage httpInputMessage = mock(HttpInputMessage.class);

        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                "JSON parse error: Unrecognized property \"value\"",
                httpInputMessage);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler
                .requestNotReadable(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).contains(UNRECOGNIZED_PROPERTY);
    }


    private MethodParameter getTestMethodParameter() throws NoSuchMethodException {
        Method method = TestController.class.getDeclaredMethod("testMethod", String.class);
        return new MethodParameter(method, 0);
    }

    private static class TestController {
        public void testMethod(String idempotencyKey) {
        }
    }

    private UUID generateRandomUuid() {
        return UUID.randomUUID();
    }
}