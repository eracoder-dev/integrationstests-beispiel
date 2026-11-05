package de.beispiel.antragsbearbeitung.api;

import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.jboss.logging.Logger;

/** Einheitliche Fehlerantwort. Der Fehler wird hier genau einmal mit Stacktrace geloggt. */
@Provider
public class FehlerMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(FehlerMapper.class);

    @Override
    public Response toResponse(Exception e) {
        if (e instanceof WebApplicationException w) {
            return Response.status(w.getResponse().getStatus()).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("fehler", String.valueOf(w.getMessage()), "korrelationsId", Korrelation.aktuell())).build();
        }
        LOG.error("Unerwarteter Fehler", e);
        return Response.serverError().type(MediaType.APPLICATION_JSON)
                .entity(Map.of("fehler", "Interner Fehler", "korrelationsId", Korrelation.aktuell())).build();
    }
}
