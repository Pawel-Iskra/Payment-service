package priv.home.paymentservice.payment.api.errorhandling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.LocatorImpl;
import priv.home.paymentservice.payment.api.dto.CreatePaymentRequest;
import priv.home.paymentservice.payment.api.dto.CreditorRequest;
import priv.home.paymentservice.payment.api.dto.DebtorRequest;
import priv.home.paymentservice.payment.exception.SinglePaymentNotFoundException;
import priv.home.paymentservice.payment.iso20022.pain001.InvalidPain001XmlException;
import priv.home.paymentservice.payment.iso20022.pain001.MappingPain001ToDomainException;
import priv.home.paymentservice.payment.iso20022.pain001.Pain001XmlErrorMessageExtractor;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ControllerExceptionHandlerTest {

    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";
    private static final BigDecimal VALID_AMOUNT = new BigDecimal("1234.56");
    private static final String CURRENCY_PLN = "PLN";
    private static final String END_TO_END_ID = "E2E-20261108-001";
    private static final String DEBTOR_IBAN = "PL93109024023152899461927776";
    private static final String CREDITOR_IBAN = "PL82109024029148361863856384";
    private static final DebtorRequest DEBTOR_REQUEST = new DebtorRequest("Debtor Name", DEBTOR_IBAN);
    private static final CreditorRequest CREDITOR_REQUEST = new CreditorRequest("Creditor Name", CREDITOR_IBAN);
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final String HEADER_IDEMPOTENCY_KEY_NOT_PRESENT = "Required header 'idempotency-key' is not present";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Amount must be greater than 0";
    private static final String CURRENCY_MUST_RESPECT_PATTERN = "Currency code must consist of exactly 3 uppercase letters";
    private static final String UNEXPECTED_ERROR_OCCURRED = "Unexpected error occurred";
    private static final String UNRECOGNIZED_PROPERTY = "Unrecognized property";
    private static final String PAIN001_XML_IS_MALFORMED = "Pain.001 XML is malformed or does not match the supported profile";
    private static final String NO_STATIC_RESOURCE = "No static resource";
    private static final String CONTENT_TYPE_NOT_SUPPORTED = "Content-Type '%s' is not supported";
    private static final String REQUEST_METHOD_NOT_SUPPORTED = "Request method '%s' is not supported";
    private static final String ACCEPTABLE_REPRESENTATIONS = "Acceptable representations: [%s].";
    private static final String REGEX_TO_REPLACE_NAMESPACE = "\\{\"urn:iso:std:iso:20022:tech:xsd:pain\\.001\\.001\\.12\":([^}]+)}";
    private static final String REGEX_TO_REMOVE_CVC_FROM_SAX_EXCEPTION = "cvc-[\\w.-]+:\\s*";
    private static final String REGEX_TO_REMOVE_FROM_XML_STREAM_EXCEPTION = "ParseError[\\s\\S]*?Message:\\s*";


    private final ControllerExceptionHandler exceptionHandler = new ControllerExceptionHandler(
            new Pain001XmlErrorMessageExtractor());


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
    public void shouldReturnBadRequestWhenConstraintMethodValidation() {
        // given
        MethodValidationResult methodValidationResultMock = mock(MethodValidationResult.class);
        HandlerMethodValidationException exception = new HandlerMethodValidationException(methodValidationResultMock);
        ParameterValidationResult parameterValidationResultMock = mock(ParameterValidationResult.class);
        when(methodValidationResultMock.getParameterValidationResults())
                .thenReturn(List.of(parameterValidationResultMock));
        when(parameterValidationResultMock.getResolvableErrors())
                .thenReturn(List.of(new DefaultMessageSourceResolvable(new String[]{}, CURRENCY_MUST_RESPECT_PATTERN)));

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.constraintMethodValidation(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).isEqualTo(CURRENCY_MUST_RESPECT_PATTERN);
    }

    @Test
    public void shouldReturnBadRequestWhenRequestAmountIsZero() throws NoSuchMethodException {
        // given
        CreatePaymentRequest notValidPaymentRequest = getPaymentRequest(new BigDecimal("0"), CURRENCY_PLN);
        DirectFieldBindingResult bindingResult = new DirectFieldBindingResult(notValidPaymentRequest, "request");
        bindingResult.rejectValue(
                AMOUNT,
                "Positive",
                AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                getTestMethodParameter(),
                bindingResult);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.notValidRequest(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).isEqualTo(AMOUNT_MUST_BE_GREATER_THAN_ZERO);
    }

    @Test
    public void shouldReturnBadRequestWhenCurrencyNotMatchesPattern() throws NoSuchMethodException {
        // given
        CreatePaymentRequest notValidPaymentRequest = getPaymentRequest(VALID_AMOUNT, "USDD");
        DirectFieldBindingResult bindingResult = new DirectFieldBindingResult(notValidPaymentRequest, "request");
        bindingResult.rejectValue(
                CURRENCY,
                "Pattern.currency",
                CURRENCY_MUST_RESPECT_PATTERN);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                getTestMethodParameter(),
                bindingResult);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.notValidRequest(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).isEqualTo(CURRENCY_MUST_RESPECT_PATTERN);
    }

    @Test
    public void shouldReturnBadRequestWhenCurrencyAndAmountAreNull() throws NoSuchMethodException {
        // given
        CreatePaymentRequest notValidPaymentRequest = getPaymentRequest(null, null);
        DirectFieldBindingResult bindingResult = new DirectFieldBindingResult(notValidPaymentRequest, "request");
        bindingResult.rejectValue(
                CURRENCY,
                "NotBlank.currency",
                CURRENCY_MUST_RESPECT_PATTERN);
        bindingResult.rejectValue(
                AMOUNT,
                "NotNull.amount",
                AMOUNT_MUST_BE_GREATER_THAN_ZERO);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                getTestMethodParameter(),
                bindingResult);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.notValidRequest(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message())
                .contains(AMOUNT_MUST_BE_GREATER_THAN_ZERO)
                .contains(CURRENCY_MUST_RESPECT_PATTERN);
    }

    @Test
    public void shouldReturnBadRequestWhenUnrecognizedProperty() {
        // given
        String errorMessage = "JSON parse error: Unrecognized property \"val\" " +
                "(class priv.home.paymentservice.payment.api.dto.CreatePaymentRequest), not marked as ignorable";
        HttpInputMessage httpInputMessage = mock(HttpInputMessage.class);

        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                errorMessage,
                httpInputMessage);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.requestNotReadable(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).contains(UNRECOGNIZED_PROPERTY);
    }

    @Test
    public void shouldReturnBadRequestNotReadableJson() {
        // given
        String errorMessage = "JSON parse error: Unexpected character ('}' (code 125)):" +
                " was expecting double-quote to start property name";
        HttpInputMessage httpInputMessage = mock(HttpInputMessage.class);

        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
                errorMessage,
                httpInputMessage);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.requestNotReadable(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).contains(errorMessage);
    }

    @Test
    public void shouldReturnBadRequestWhenMappingPain001ToDomainException() {
        // given
        String exceptionMessage = "Group header is required";
        MappingPain001ToDomainException exception = new MappingPain001ToDomainException(exceptionMessage);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler
                .mappingPain001ToDomainException(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).contains(exceptionMessage);
    }

    @Test
    public void shouldReturnBadRequestWhenInvalidPain001XmlException() {
        // given
        InvalidPain001XmlException exception = new InvalidPain001XmlException(PAIN001_XML_IS_MALFORMED);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.invalidPain001xml(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).contains(PAIN001_XML_IS_MALFORMED);
    }

    @Test
    public void shouldReturnBadRequestWhenSAXParseException() {
        // given
        String saxParseExceptionMessage = "cvc-complex-type.2.4.a: Invalid content was found starting with element " +
                "'{\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.12\":GrpH}'. " +
                "One of '{\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.12\":GrpHdr}' is expected.";
        String responseMessage = saxParseExceptionMessage
                .replaceAll(REGEX_TO_REMOVE_CVC_FROM_SAX_EXCEPTION, "")
                .replaceAll(REGEX_TO_REPLACE_NAMESPACE, "$1");
        int lineNumber = 4;
        int columnNumber = 15;
        LocatorImpl locator = new LocatorImpl();
        locator.setLineNumber(lineNumber);
        locator.setColumnNumber(columnNumber);
        SAXParseException saxParseException = new SAXParseException(
                saxParseExceptionMessage,
                locator);
        InvalidPain001XmlException exception = new InvalidPain001XmlException(
                PAIN001_XML_IS_MALFORMED,
                saxParseException);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.invalidPain001xml(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message())
                .contains("Line %d".formatted(lineNumber))
                .contains("column %d".formatted(columnNumber))
                .contains(responseMessage);
    }

    @Test
    public void shouldReturnBadRequestWhenXMLStreamException() {
        // given
        String xmlStreamExceptionMessage = "ParseError at [row,col]:[1,1]\nMessage: Content is not allowed in prolog.";
        String responseMessage = xmlStreamExceptionMessage
                .replaceAll(REGEX_TO_REMOVE_FROM_XML_STREAM_EXCEPTION, "");
        int lineNumber = 1;
        int columnNumber = 1;
        Location locationMock = mock(Location.class);
        when(locationMock.getLineNumber()).thenReturn(lineNumber);
        when(locationMock.getColumnNumber()).thenReturn(columnNumber);

        XMLStreamException saxParseException = new XMLStreamException(
                xmlStreamExceptionMessage,
                locationMock);
        InvalidPain001XmlException exception = new InvalidPain001XmlException(
                PAIN001_XML_IS_MALFORMED,
                saxParseException);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.invalidPain001xml(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message())
                .contains("Line %d".formatted(lineNumber))
                .contains("column %d".formatted(columnNumber))
                .contains(responseMessage);
    }

    @Test
    public void shouldReturnNotFoundWhenWrongPathWasCalled() {
        // given
        String resourcePath = "createpayments/pain001";
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.POST,
                "/createpayments/pain001",
                resourcePath);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.noResourceFound(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(responseResponseEntity.getBody().message())
                .contains(resourcePath)
                .contains(NO_STATIC_RESOURCE);
    }

    @Test
    public void shouldReturn415ForUnsupportedMediaType() {
        // given
        MediaType requestedNotSupported = MediaType.APPLICATION_XML;
        HttpMediaTypeNotSupportedException exception = new HttpMediaTypeNotSupportedException(
                requestedNotSupported,
                List.of(MediaType.APPLICATION_JSON),
                HttpMethod.POST);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.unsupportedMediatype(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(responseResponseEntity.getBody().message())
                .contains(CONTENT_TYPE_NOT_SUPPORTED.formatted(requestedNotSupported));
    }

    @Test
    public void shouldReturn405ForMethodNotAllowed() {
        // given
        String requestedNotSupported = HttpMethod.GET.name();
        HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException(
                requestedNotSupported);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.methodNotSupported(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(responseResponseEntity.getBody().message())
                .contains(REQUEST_METHOD_NOT_SUPPORTED.formatted(requestedNotSupported));
    }

    @Test
    public void shouldReturnBadRequestForArgumentMismatch() throws NoSuchMethodException {
        // given
        String invalidUuid = generateRandomUuid().toString().replace("-", "");
        String exceptionMessage = "Invalid UUID string: %s".formatted(invalidUuid);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                invalidUuid,
                UUID.class,
                "id",
                getTestMethodParameter(),
                new IllegalArgumentException(exceptionMessage));

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.argumentMismatch(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(responseResponseEntity.getBody().message()).contains(exceptionMessage);
    }

    @Test
    public void shouldReturnNotAcceptableWhenRequestExpectedMediaTypeIsNotSupported() {
        // given
        MediaType supportedReturnedType = MediaType.APPLICATION_JSON;
        HttpMediaTypeNotAcceptableException exception = new HttpMediaTypeNotAcceptableException(
                List.of(supportedReturnedType));

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.mediaTypeNotAcceptable(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(responseResponseEntity.getBody().message())
                .contains(ACCEPTABLE_REPRESENTATIONS.formatted(supportedReturnedType.toString()));
    }

    @Test
    public void shouldReturnIntervalServerErrorWhenUnexpectedError(CapturedOutput output) {
        // given
        String exceptionMessage = "Simulated NPE";
        NullPointerException exception = new NullPointerException(exceptionMessage);

        // when
        ResponseEntity<ErrorResponse> responseResponseEntity = exceptionHandler.technicalException(exception);

        // then
        assertThat(responseResponseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(responseResponseEntity.getBody()).isNotNull();
        assertThat(responseResponseEntity.getBody().httpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(responseResponseEntity.getBody().message()).contains(UNEXPECTED_ERROR_OCCURRED);
        assertThat(output.getOut()).contains(exceptionMessage);
        assertThat(output.getOut()).contains(NullPointerException.class.getName());
    }


    private CreatePaymentRequest getPaymentRequest(BigDecimal amount, String currency) {
        return CreatePaymentRequest.builder()
                .amount(amount)
                .currency(currency)
                .debtorRequest(DEBTOR_REQUEST)
                .creditorRequest(CREDITOR_REQUEST)
                .endToEndId(END_TO_END_ID)
                .build();
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