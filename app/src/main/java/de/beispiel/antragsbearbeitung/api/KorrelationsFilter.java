package de.beispiel.antragsbearbeitung.api;

import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Jeder Request bekommt eine Korrelations-ID. Sie kommt aus dem Header X-Correlation-ID oder wird neu erzeugt,
 * steht in jeder Logzeile dieses Requests und geht in der Antwort zurück an den Aufrufer.
 */
@Provider
public class KorrelationsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-ID";
    private static final Logger LOG = Logger.getLogger(KorrelationsFilter.class);

    @Override
    public void filter(ContainerRequestContext anfrage) {
        String id = anfrage.getHeaderString(HEADER);
        Korrelation.setze(id != null && !id.isBlank() ? id : UUID.randomUUID().toString());
        String akteur = anfrage.getHeaderString("X-Benutzer");
        Korrelation.setzeAkteur(akteur != null && !akteur.isBlank() ? akteur : "demo");
        LOG.debugf("%s %s", anfrage.getMethod(), anfrage.getUriInfo().getRequestUri().getPath());
    }

    @Override
    public void filter(ContainerRequestContext anfrage, ContainerResponseContext antwort) {
        antwort.getHeaders().putSingle(HEADER, Korrelation.aktuell());
        Korrelation.aufraeumen();
    }
}
