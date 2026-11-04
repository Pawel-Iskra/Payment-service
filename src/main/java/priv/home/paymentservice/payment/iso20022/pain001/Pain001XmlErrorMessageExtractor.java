package priv.home.paymentservice.payment.iso20022.pain001;

import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;

import javax.xml.stream.XMLStreamException;
import java.util.Optional;

@Component
public class Pain001XmlErrorMessageExtractor {

    private static final String PAIN001_XML_MALFORMED = "Pain.001 XML is malformed or does not match the supported profile";
    private static final String PAIN001_NAMESPACE = "\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.12\":";
    private static final String RESPONSE_MESSAGE_FORMAT = "Line %d, column %d: %s";
    private static final String REGEX_TO_REMOVE_FROM_XML_STREAM_EXCEPTION = "ParseError[\\s\\S]*?Message:\\s*";
    private static final String REGEX_TO_REMOVE_CVC_FROM_SAX_EXCEPTION = "cvc-[\\w.-]+:\\s*";


    public String extractMessageFromPain001XmlParseException(InvalidPain001XmlException pain001XmlException) {
        String responseMessage = PAIN001_XML_MALFORMED;
        Optional<Throwable> parseExceptionOptional = findParseException(pain001XmlException);
        if (parseExceptionOptional.isEmpty() || parseExceptionOptional.get().getMessage() == null) {
            return responseMessage;
        }
        Throwable exception = parseExceptionOptional.get();
        if (exception instanceof SAXParseException saxException) {
            int lineNumber = saxException.getLineNumber();
            int columnNumber = saxException.getColumnNumber();
            String message = saxException.getMessage()
                    .replace(PAIN001_NAMESPACE, "")
                    .replaceAll("[{}]", "")
                    .replaceAll(REGEX_TO_REMOVE_CVC_FROM_SAX_EXCEPTION, "");
            responseMessage = RESPONSE_MESSAGE_FORMAT.formatted(
                    lineNumber,
                    columnNumber,
                    message);
        } else if (exception instanceof XMLStreamException xmlStreamException) {
            String message = xmlStreamException.getMessage()
                    .replaceAll(REGEX_TO_REMOVE_FROM_XML_STREAM_EXCEPTION, "");
            if (xmlStreamException.getLocation() != null) {
                int lineNumber = xmlStreamException.getLocation().getLineNumber();
                int columnNumber = xmlStreamException.getLocation().getColumnNumber();
                responseMessage = RESPONSE_MESSAGE_FORMAT.formatted(
                        lineNumber,
                        columnNumber,
                        message);
            } else {
                responseMessage = message;
            }
        }
        return responseMessage;
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
}
