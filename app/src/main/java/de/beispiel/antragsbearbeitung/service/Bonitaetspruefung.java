package de.beispiel.antragsbearbeitung.service;

import de.beispiel.antragsbearbeitung.soap.BonitaetsErgebnis;
import de.beispiel.antragsbearbeitung.soap.BonitaetsPort;
import de.beispiel.antragsbearbeitung.soap.BonitaetsService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceException;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

/**
 * Ruft die externe SOAP-Schnittstelle "Bonitätsauskunft" auf. Der Client stammt aus der WSDL
 * (src/main/resources/wsdl/bonitaet.wsdl) und wird beim Bauen erzeugt.
 *
 * <p>Fällt der Dienst aus oder meldet er einen SOAP-Fault, liefert der Fallback "unbekannt". Der Antrag
 * geht dann in die manuelle Prüfung, statt zu scheitern. Einen Circuit Breaker gibt es hier bewusst nicht:
 * Sein Zustand würde zwischen den Integrationstests hängen bleiben.
 */
@ApplicationScoped
public class Bonitaetspruefung {

    private static final Logger LOG = Logger.getLogger(Bonitaetspruefung.class);

    @Inject
    @ConfigProperty(name = "bonitaetsdienst.url")
    String url;

    @Inject
    @ConfigProperty(name = "bonitaetsdienst.verbindungs-timeout-ms", defaultValue = "1000")
    int verbindungsTimeoutMs;

    @Inject
    @ConfigProperty(name = "bonitaetsdienst.lese-timeout-ms", defaultValue = "1500")
    int leseTimeoutMs;

    private BonitaetsService service;

    @PostConstruct
    void start() {
        // Die WSDL kommt aus dem Klassenpfad. Der Standardkonstruktor des generierten Service würde den
        // Pfad des Build-Rechners benutzen, den es im Container nicht gibt.
        URL wsdl = Bonitaetspruefung.class.getResource("/wsdl/bonitaet.wsdl");
        service = new BonitaetsService(wsdl);
        LOG.infof("Bonitätsdienst unter %s, Zeitlimits %d/%d ms", url, verbindungsTimeoutMs, leseTimeoutMs);
    }

    @Timeout(4000)
    @Retry(maxRetries = 1, delay = 200, retryOn = BonitaetsdienstException.class)
    @Fallback(fallbackMethod = "unbekannt")
    public Bonitaet pruefe(String name, String plz, BigDecimal summe) {
        // Ein Port pro Aufruf: Die Proxys von JAX-WS sind nicht für gleichzeitige Nutzung gedacht.
        BonitaetsPort port = service.getBonitaetsPort();
        Map<String, Object> kontext = ((BindingProvider) port).getRequestContext();
        kontext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, url);
        kontext.put("jakarta.xml.ws.client.connectionTimeout", verbindungsTimeoutMs);
        kontext.put("jakarta.xml.ws.client.receiveTimeout", leseTimeoutMs);
        try {
            BonitaetsErgebnis ergebnis = port.pruefeBonitaet(name, plz, summe);
            return Bonitaet.ausScore(ergebnis.getScore());
        } catch (WebServiceException e) {
            // Auch jakarta.xml.ws.soap.SOAPFaultException ist eine WebServiceException.
            throw new BonitaetsdienstException("Bonitätsdienst: " + e.getMessage(), e);
        }
    }

    /** Plan B: Der Antrag läuft weiter und wird von einem Menschen entschieden. */
    Bonitaet unbekannt(String name, String plz, BigDecimal summe) {
        LOG.warnf("Bonitätsauskunft für PLZ %s nicht möglich, Ergebnis ist unbekannt", plz);
        return Bonitaet.UNBEKANNT;
    }
}
