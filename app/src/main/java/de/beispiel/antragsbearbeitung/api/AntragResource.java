package de.beispiel.antragsbearbeitung.api;

import de.beispiel.antragsbearbeitung.domain.Antrag;
import de.beispiel.antragsbearbeitung.prozess.ProzessMotor;
import de.beispiel.antragsbearbeitung.service.AntragService;
import de.beispiel.antragsbearbeitung.service.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.task.Task;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AntragResource {

    public record AntragEingabe(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "\\d{5}", message = "PLZ muss fünfstellig sein") String plz,
            @NotBlank String ort,
            @NotNull @DecimalMin("1") BigDecimal antragssumme) {
    }

    public record AntragDto(String antragsnummer, String name, String plz, String ort, BigDecimal antragssumme,
                            String status, String prozessInstanzId, LocalDateTime erfasstAm) {
        static AntragDto von(Antrag a) {
            return new AntragDto(a.getAntragsnummer(), a.getName(), a.getPlz(), a.getOrt(), a.getAntragssumme(),
                    a.getStatus(), a.getProzessInstanzId(), a.getErfasstAm());
        }
    }

    public record AufgabeDto(String id, String name, String antragsnummer, String gruppe, java.util.Date erstellt) {
    }

    public record Entscheidung(@NotBlank @Pattern(regexp = "annehmen|ablehnen") String entscheidung) {
    }

    @Inject
    AntragService antraege;

    @Inject
    ProzessMotor motor;

    @Inject
    AuditService audit;

    @POST
    @Path("/antraege")
    @Operation(summary = "Antrag erfassen und den Prozess starten",
            description = "PLZ 00000 lässt den Adressdienst ausfallen, 99999 macht ihn langsam, 11111 liefert eine ungültige Adresse, 22222 fällt jeden zweiten Aufruf aus.")
    public Response erfassen(@Valid AntragEingabe e) {
        Antrag a = antraege.erfassen(e.name(), e.plz(), e.ort(), e.antragssumme());
        return Response.status(Response.Status.ACCEPTED).entity(AntragDto.von(a)).build();
    }

    @GET
    @Path("/antraege/{nummer}")
    public AntragDto antrag(@PathParam("nummer") String nummer) {
        Antrag a = antraege.finden(nummer);
        if (a == null) {
            throw new NotFoundException("Antrag " + nummer + " nicht gefunden");
        }
        return AntragDto.von(a);
    }

    @GET
    @Path("/aufgaben")
    @Operation(summary = "Offene Aufgaben aus Camunda (manuelle Prüfung, Fehler im Sollstellungslauf)")
    public List<AufgabeDto> aufgaben() {
        return motor.aufgaben().createTaskQuery().orderByTaskCreateTime().asc().list().stream()
                .map(t -> new AufgabeDto(t.getId(), t.getName(),
                        (String) motor.aufgaben().getVariable(t.getId(), "antragsnummer"),
                        t.getTaskDefinitionKey(), t.getCreateTime()))
                .toList();
    }

    @POST
    @Path("/aufgaben/{id}/abschluss")
    @Operation(summary = "Manuelle Prüfung abschließen: annehmen oder ablehnen")
    public Response abschliessen(@PathParam("id") String id, @Valid Entscheidung e) {
        Task aufgabe = motor.aufgaben().createTaskQuery().taskId(id).singleResult();
        if (aufgabe == null) {
            throw new NotFoundException("Aufgabe " + id + " nicht gefunden");
        }
        audit.protokolliere("AUFGABE_ABGESCHLOSSEN", id, aufgabe.getName() + ": " + e.entscheidung());
        motor.aufgaben().complete(id, Map.of("entscheidung", e.entscheidung()));
        return Response.noContent().build();
    }
}
