package de.beispiel.antragsbearbeitung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Vertragstest: Die Stub-Antworten, mit denen die Integrationstests den Bonitätsdienst ersetzen, müssen zum
 * Schema der WSDL passen. Ändert sich die WSDL oder tippt jemand den Stub falsch, schlägt dieser schnelle
 * Test an, lange bevor ein Integrationstest mit Containern läuft. Ohne ihn könnte der Stub still vom echten
 * Dienst abweichen, und alle Tests blieben grün.
 */
class SoapVertragTest {

    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static Schema schema;

    @BeforeAll
    static void schemaAusDerWsdlLesen() throws Exception {
        Document wsdl = dokument(Path.of("src/main/resources/wsdl/bonitaet.wsdl"));
        NodeList schemas = wsdl.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema");
        assertThat(schemas.getLength()).as("Schema in der WSDL").isEqualTo(1);
        schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(new DOMSource(schemas.item(0)));
    }

    private static Document dokument(Path datei) throws Exception {
        DocumentBuilderFactory fabrik = DocumentBuilderFactory.newInstance();
        fabrik.setNamespaceAware(true);
        try (InputStream in = Files.newInputStream(datei)) {
            DocumentBuilder bauer = fabrik.newDocumentBuilder();
            return bauer.parse(in);
        }
    }

    /** Das erste Element im Body des SOAP-Umschlags, das ist der eigentliche Inhalt. */
    private static Element inhalt(String datei) throws Exception {
        Document umschlag = dokument(Path.of("src/test/resources/soap", datei));
        Node body = umschlag.getElementsByTagNameNS(SOAP, "Body").item(0);
        for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        throw new IllegalStateException("Kein Inhalt im SOAP-Body von " + datei);
    }

    private static void pruefe(Element inhalt) throws Exception {
        Validator validator = schema.newValidator();
        validator.validate(new DOMSource(inhalt));
    }

    @Test
    void stubAntwortGutPasstZurWsdl() throws Exception {
        pruefe(inhalt("antwort-gut.xml"));
    }

    @Test
    void stubAntwortSchlechtPasstZurWsdl() throws Exception {
        pruefe(inhalt("antwort-schlecht.xml"));
    }

    @Test
    void einFaultHatKeinenInhaltImSinneDerWsdl() throws Exception {
        // Ein Fault ist im SOAP-Umschlag etwas anderes als eine Antwort der Operation.
        Element fault = inhalt("fault-server.xml");
        assertThat(fault.getLocalName()).isEqualTo("Fault");
        assertThatThrownBy(() -> pruefe(fault)).isInstanceOf(SAXException.class);
    }

    @Test
    void eineFalscheAntwortWirdErkannt() throws Exception {
        Element antwort = inhalt("antwort-gut.xml");
        // Das Pflichtfeld "bewertung" entfernen: So sähe ein Stub aus, der vom Vertrag abweicht.
        Node ergebnis = antwort.getElementsByTagNameNS("http://beispiel.de/bonitaet", "ergebnis").item(0);
        Node bewertung = ((Element) ergebnis).getElementsByTagNameNS("http://beispiel.de/bonitaet", "bewertung").item(0);
        ergebnis.removeChild(bewertung);
        assertThatThrownBy(() -> pruefe(antwort)).isInstanceOf(SAXException.class);
    }
}
