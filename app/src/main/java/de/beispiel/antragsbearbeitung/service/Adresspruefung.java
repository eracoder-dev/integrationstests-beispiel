package de.beispiel.antragsbearbeitung.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.jboss.logging.Logger;

/**
 * Ruft den externen Adressdienst auf und schützt die Anwendung vor seinen Fehlern.
 *
 * <p>Zusammenspiel bei einem Ausfall: Jeder Versuch hat ein Zeitlimit. Retry wiederholt zweimal
 * mit Pause. Der Circuit Breaker zählt jeden einzelnen Versuch und öffnet bei zu vielen Fehlern.
 * Das Bulkhead begrenzt die gleichzeitigen Aufrufe. Bleibt am Ende ein Fehler übrig, liefert der
 * Fallback "offen", und die Prüfung wird später nachgeholt.
 *
 * <p>Die fachliche Antwort "Adresse ungültig" ist ein Ergebnis und kein Ausfall: Sie wird nicht
 * wiederholt, nicht als Fehler gezählt und löst keinen Fallback aus.
 */
@ApplicationScoped
public class Adresspruefung {

    private static final Logger LOG = Logger.getLogger(Adresspruefung.class);

    @Inject
    @ConfigProperty(name = "adressdienst.url")
    String url;

    @Inject
    @ConfigProperty(name = "adressdienst.verbindungs-timeout-ms", defaultValue = "500")
    long verbindungsTimeoutMs;

    @Inject
    @ConfigProperty(name = "adressdienst.lese-timeout-ms", defaultValue = "1500")
    long leseTimeoutMs;

    private Client client;

    @PostConstruct
    void start() {
        // Zeitlimits am HTTP-Client: Das FT-Timeout allein kann blockierendes I/O nicht sicher abbrechen.
        client = ClientBuilder.newBuilder()
                .connectTimeout(verbindungsTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(leseTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        LOG.infof("Adressdienst unter %s, Zeitlimits %d/%d ms", url, verbindungsTimeoutMs, leseTimeoutMs);
    }

    @PreDestroy
    void ende() {
        client.close();
    }

    @Timeout(value = 3000)
    @Retry(maxRetries = 2, delay = 200, jitter = 100,
            abortOn = {AdresseUngueltigException.class, CircuitBreakerOpenException.class})
    @CircuitBreaker(requestVolumeThreshold = 6, failureRatio = 0.5, delay = 10000, successThreshold = 2,
            skipOn = AdresseUngueltigException.class)
    @Bulkhead(5)
    @Fallback(fallbackMethod = "spaeterPruefen", skipOn = AdresseUngueltigException.class)
    public AdressErgebnis pruefe(String plz, String ort) {
        LOG.debugf("Prüfe Adresse %s %s", plz, ort);
        try (Response antwort = client.target(url).path("adressen/pruefen")
                .queryParam("plz", plz).queryParam("ort", ort)
                .request(MediaType.APPLICATION_JSON).get()) {
            if (antwort.getStatus() >= 500) {
                throw new AdressdienstException("Adressdienst antwortet mit Status " + antwort.getStatus());
            }
            AdressAntwort a = antwort.readEntity(AdressAntwort.class);
            if (!a.gueltig()) {
                throw new AdresseUngueltigException(a.grund());
            }
            return AdressErgebnis.GUELTIG;
        } catch (ProcessingException e) {
            throw new AdressdienstException("Adressdienst nicht erreichbar: " + e.getMessage(), e);
        }
    }

    /** Plan B: Der Antrag läuft weiter, die Prüfung wird später nachgeholt. */
    AdressErgebnis spaeterPruefen(String plz, String ort) {
        LOG.warnf("Adressprüfung für %s %s nicht möglich, Ergebnis ist offen", plz, ort);
        return AdressErgebnis.OFFEN;
    }
}
