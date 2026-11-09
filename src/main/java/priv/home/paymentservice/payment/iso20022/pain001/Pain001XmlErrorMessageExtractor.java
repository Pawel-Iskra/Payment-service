package priv.home.paymentservice.payment.iso20022.pain001;

import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;

import javax.xml.stream.XMLStreamException;
import java.util.Optional;

@Component
public class Pain001XmlErrorMessageExtractor {

    private static final String PAIN001_NAMESPACE = "\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.12\":";
    private static final String PAIN001_XML_MALFORMED = "Pain.001 XML is malformed or does not match the supported profile";
    private static final String RESPONSE_MESSAGE_FORMAT = "Line %d, column %d: %s";
    private static final String REGEX_TO_REMOVE_PART_OF_MESSAGE_FROM_XML_STREAM_EXCEPTION = "ParseError[\\s\\S]*?Message:\\s*";
    private static final String REGEX_TO_REPLACE_NAMESPACE = "\\{\"urn:iso:std:iso:20022:tech:xsd:pain\\.001\\.001\\.12\":([^}]+)}";
    private static final String REGEX_TO_REMOVE_CVC_FROM_SAX_EXCEPTION = "cvc-[\\w.-]+:\\s*";
    private static final String CURRENCY_MUST_RESPECT_PATTERN = "Currency code must consist of exactly 3 uppercase letters";
    private static final String CURRENCY_IS_REQUIRED = "Currency is required";
    private static final String AMOUNT_MUST_BE_GREATER_THAN_ZERO = "Amount must be greater than 0";
    private static final String AMOUNT_MUST_NOT_EXCEED = "Amount must not exceed 9999999999.99";
    private static final String AMOUNT_MUST_HAVE_AT_MOST_DEC_PLACES = "Amount must have at most 2 decimal places";


    public String extractMessageFromPain001XmlParseException(InvalidPain001XmlException pain001XmlException) {
        String responseMessage = extractResponseMessageFromXmlParseException(pain001XmlException);
        return normalizePain001XmlValidationResponseMessage(responseMessage);
    }


    private String extractResponseMessageFromXmlParseException(InvalidPain001XmlException pain001XmlException) {
        Optional<Throwable> parseExceptionOptional = findParseException(pain001XmlException);
        if (parseExceptionOptional.isEmpty() || parseExceptionOptional.get().getMessage() == null) {
            return PAIN001_XML_MALFORMED;
        }

        Throwable exception = parseExceptionOptional.get();
        if (exception instanceof SAXParseException saxException) {
            int lineNumber = saxException.getLineNumber();
            int columnNumber = saxException.getColumnNumber();
            String message = saxException.getMessage()
                    .replaceAll(REGEX_TO_REPLACE_NAMESPACE, "$1")
                    .replaceAll(REGEX_TO_REMOVE_CVC_FROM_SAX_EXCEPTION, "");
            return RESPONSE_MESSAGE_FORMAT.formatted(
                    lineNumber,
                    columnNumber,
                    message);
        } else if (exception instanceof XMLStreamException xmlStreamException) {
            String message = xmlStreamException.getMessage()
                    .replaceAll(REGEX_TO_REMOVE_PART_OF_MESSAGE_FROM_XML_STREAM_EXCEPTION, "");
            if (xmlStreamException.getLocation() != null) {
                int lineNumber = xmlStreamException.getLocation().getLineNumber();
                int columnNumber = xmlStreamException.getLocation().getColumnNumber();
                return RESPONSE_MESSAGE_FORMAT.formatted(
                        lineNumber,
                        columnNumber,
                        message);
            } else {
                return message;
            }
        }
        return PAIN001_XML_MALFORMED;
    }

    private Optional<Throwable> findParseException(Throwable exception) {
        Throwable currentCouseException = exception;
        while (currentCouseException != null) {
            if (currentCouseException instanceof SAXParseException || currentCouseException instanceof XMLStreamException) {
                return Optional.of(currentCouseException);
            }
            currentCouseException = currentCouseException.getCause();
        }
        return Optional.empty();
    }

    // TODO: better would be to introduce common error enum, then map errors from JSON Bean validation and from XML XSD
    //  validation to common error values and in ControllerExceptionHandler map them into specific response messages
    private String normalizePain001XmlValidationResponseMessage(String responseMessage) {
        // aligning with JSON currency/amount validation response messages
        if (responseMessage.contains("minExclusive")) {
            return AMOUNT_MUST_BE_GREATER_THAN_ZERO;
        }
        if (responseMessage.contains("fractionDigits") || responseMessage.contains("fraction digits")) {
            return AMOUNT_MUST_HAVE_AT_MOST_DEC_PLACES;
        }
        if (responseMessage.contains("maxInclusive")) {
            return AMOUNT_MUST_NOT_EXCEED;
        }
        if (responseMessage.contains("[A-Z]{3,3}")) {
            return CURRENCY_MUST_RESPECT_PATTERN;
        }
        return responseMessage;
    }
}
