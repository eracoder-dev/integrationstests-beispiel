package de.beispiel.antragsbearbeitung.api;

import de.beispiel.antragsbearbeitung.batch.BatchSteuerung;
import de.beispiel.antragsbearbeitung.batch.LaufStatus;
import de.beispiel.antragsbearbeitung.prozess.ProzessMotor;
import de.beispiel.antragsbearbeitung.service.AdressErgebnis;
import de.beispiel.antragsbearbeitung.service.AdresseUngueltigException;
import de.beispiel.antragsbearbeitung.service.Adresspruefung;
import de.beispiel.antragsbearbeitung.service.AuditService;
import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class BatchResource {

    @Inject
    BatchSteuerung batch;

    @Inject
    ProzessMotor motor;

    @Inject
    AuditService audit;

    @Inject
    Adresspruefung adresse;

    @POST
    @Path("/batch/sollstellung")
    @Operation(summary = "Sollstellung direkt als Batchlauf starten",
            description = "failAfter=N lässt den Lauf nach N Datensätzen scheitern, um den Neustart auszuprobieren.")
    public Response starten(@QueryParam("monat") String monat, @QueryParam("failAfter") @DefaultValue("0") int failAfter) {
        String m = monat != null ? monat : YearMonth.now().toString();
        long id = batch.starten(m, failAfter);
        return Response.accepted(Map.of("ausfuehrungsId", id, "monat", m)).build();
    }

    @GET
    @Path("/batch/{id}")
    @Operation(summary = "Stand eines Laufs mit Zählern")
    public LaufStatus status(@PathParam("id") long id) {
        try {
            return batch.status(id);
        } catch (RuntimeException e) {
            throw new NotFoundException("Ausführung " + id + " nicht gefunden");
        }
    }

    @POST
    @Path("/batch/{id}/neustart")
    @Operation(summary = "Abgebrochenen Lauf beim letzten Checkpoint fortsetzen")
    public Response neustart(@PathParam("id") long id) {
        long neu = batch.neustarten(id);
        return Response.accepted(Map.of("ausfuehrungsId", neu, "vorgaenger", id)).build();
    }

    @POST
    @Path("/prozesse/sollstellung")
    @Operation(summary = "Sollstellung als Camunda-Prozess starten",
            description = "Der Prozess startet den Batchlauf, wartet auf sein Ende und holt bei einem Fehler einen Menschen.")
    public Response prozessStarten(@QueryParam("monat") String monat, @QueryParam("failAfter") @DefaultValue("0") int failAfter) {
        String m = monat != null ? monat : YearMonth.now().toString();
        ProcessInstance instanz = motor.runtime().startProcessInstanceByKey("sollstellungslauf", "sollstellung-" + m,
                Map.of("monat", m, "failAfter", failAfter, "korrelationsId", Korrelation.aktuell(),
                        "akteur", Korrelation.akteur()));
        audit.protokolliere("SOLLSTELLUNGSPROZESS_GESTARTET", instanz.getId(), "Monat " + m);
        return Response.accepted(Map.of("prozessInstanzId", instanz.getId(), "monat", m)).build();
    }

    @GET
    @Path("/prozesse/{id}")
    @Operation(summary = "Stand einer Prozessinstanz mit ihren Variablen")
    public Map<String, Object> prozess(@PathParam("id") String id) {
        HistoricProcessInstance h = motor.historie().createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
        if (h == null) {
            throw new NotFoundException("Prozessinstanz " + id + " nicht gefunden");
        }
        Map<String, Object> variablen = new LinkedHashMap<>();
        List<HistoricVariableInstance> liste = motor.historie().createHistoricVariableInstanceQuery().processInstanceId(id).list();
        liste.forEach(v -> variablen.put(v.getName(), v.getValue()));
        Map<String, Object> antwort = new LinkedHashMap<>();
        antwort.put("id", id);
        antwort.put("prozess", h.getProcessDefinitionKey());
        antwort.put("zustand", h.getState());
        antwort.put("beendet", h.getEndTime() != null);
        antwort.put("variablen", variablen);
        return antwort;
    }

    @GET
    @Path("/adresse/pruefen")
    @Operation(summary = "Adressdienst direkt aufrufen, um Fault Tolerance zu beobachten",
            description = "PLZ 00000 Ausfall, 99999 langsam, 11111 ungültig, 22222 jeder zweite Aufruf fällt aus.")
    public Map<String, Object> adressePruefen(@QueryParam("plz") String plz, @QueryParam("ort") @DefaultValue("München") String ort) {
        long start = System.currentTimeMillis();
        Map<String, Object> antwort = new LinkedHashMap<>();
        try {
            AdressErgebnis e = adresse.pruefe(plz, ort);
            antwort.put("ergebnis", e.name());
        } catch (AdresseUngueltigException e) {
            antwort.put("ergebnis", "UNGUELTIG");
            antwort.put("grund", e.getMessage());
        } catch (RuntimeException e) {
            // Kein Fallback ausgelöst, etwa weil der Bulkhead voll war: der Fehler geht an den Aufrufer.
            antwort.put("ergebnis", "FEHLER");
            antwort.put("grund", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        antwort.put("dauerMs", System.currentTimeMillis() - start);
        return antwort;
    }
}
