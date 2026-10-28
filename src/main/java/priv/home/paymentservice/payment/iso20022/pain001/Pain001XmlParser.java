package priv.home.paymentservice.payment.iso20022.pain001;

import iso.std.iso._20022.tech.xsd.pain_001_001.Document;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.net.URL;

@Slf4j
@Component
public class Pain001XmlParser {

    private static final String LIMITED_PAIN001_XSD_PATH = "/iso20022/xsd/limited-pain001.xsd";

    private final JAXBContext jaxbContext;
    private final Schema limitedPain001Schema;

    public Pain001XmlParser() throws JAXBException, SAXException {
        this.jaxbContext = JAXBContext.newInstance(Document.class);
        URL limitedPain001xsdUrl = getClass().getResource(LIMITED_PAIN001_XSD_PATH);
        if (limitedPain001xsdUrl == null) {
            throw new IllegalStateException("Not found limited-pain001.xsd");
        }
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        this.limitedPain001Schema = schemaFactory.newSchema(limitedPain001xsdUrl);
    }

    public Document parse(InputStream xmlInputStream) {
        if (xmlInputStream == null) {
            throw new InvalidPain001XmlException("Input stream of XML is null");
        }
        XMLStreamReader xmlStreamReader = null;
        try {
            XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
            xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(xmlInputStream);

            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            unmarshaller.setSchema(limitedPain001Schema);
            JAXBElement<Document> unmarshallResult = unmarshaller.unmarshal(xmlStreamReader, Document.class);
            return unmarshallResult.getValue();
        } catch (XMLStreamException exception) {
            throw new InvalidPain001XmlException("Malformed Pain001 XML", exception);
        } catch (JAXBException exception) {
            throw new InvalidPain001XmlException("Pain001 XML does not match the supported profile", exception);
        } finally {
            closeStreamReader(xmlStreamReader);
        }
    }


    private void closeStreamReader(XMLStreamReader xmlStreamReader) {
        if (xmlStreamReader != null) {
            try {
                xmlStreamReader.close();
            } catch (XMLStreamException exception) {
                // ignored not to mask possible parse exception
                log.debug("Failed to close XMLStreamReader", exception);
            }
        }
    }

}
