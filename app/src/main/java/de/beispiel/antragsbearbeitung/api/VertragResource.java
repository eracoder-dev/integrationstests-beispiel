package de.beispiel.antragsbearbeitung.api;

import de.beispiel.antragsbearbeitung.domain.AuditEintrag;
import de.beispiel.antragsbearbeitung.domain.Forderung;
import de.beispiel.antragsbearbeitung.domain.Vertrag;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class VertragResource {

    public record VertragDto(String nummer, String vertragspartner, BigDecimal monatsbeitrag, String status) {
    }

    public record ForderungDto(String vertragNummer, String monat, BigDecimal betrag) {
    }

    public record AuditDto(LocalDateTime zeitpunkt, String aktion, String objekt, String details,
                           String korrelationsId, String akteur) {
    }

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @GET
    @Path("/vertraege")
    @Operation(summary = "Verträge auflisten")
    public List<VertragDto> vertraege(@QueryParam("limit") @DefaultValue("20") int limit) {
        return em.createQuery("select v from Vertrag v order by v.nummer", Vertrag.class)
                .setMaxResults(Math.min(limit, 200)).getResultList().stream()
                .map(v -> new VertragDto(v.getNummer(), v.getVertragspartner(), v.getMonatsbeitrag(), v.getStatus()))
                .toList();
    }

    @GET
    @Path("/vertraege/{nummer}")
    public VertragDto vertrag(@PathParam("nummer") String nummer) {
        Vertrag v = em.find(Vertrag.class, nummer);
        if (v == null) {
            throw new NotFoundException("Vertrag " + nummer + " nicht gefunden");
        }
        return new VertragDto(v.getNummer(), v.getVertragspartner(), v.getMonatsbeitrag(), v.getStatus());
    }

    @GET
    @Path("/forderungen")
    @Operation(summary = "Forderungen eines Monats, mit Anzahl und Summe")
    public Map<String, Object> forderungen(@QueryParam("monat") String monat, @QueryParam("limit") @DefaultValue("10") int limit) {
        Object[] summe = (Object[]) em.createQuery(
                        "select count(f), coalesce(sum(f.betrag), 0) from Forderung f where f.monat = :m")
                .setParameter("m", monat).getSingleResult();
        List<ForderungDto> beispiele = em.createQuery("select f from Forderung f where f.monat = :m order by f.id", Forderung.class)
                .setParameter("m", monat).setMaxResults(Math.min(limit, 100)).getResultList().stream()
                .map(f -> new ForderungDto(f.getVertragNummer(), f.getMonat(), f.getBetrag())).toList();
        return Map.of("monat", monat, "anzahl", summe[0], "summe", summe[1], "beispiele", beispiele);
    }

    @GET
    @Path("/audit")
    @Operation(summary = "Nachvollziehbarkeit: die letzten Einträge")
    public List<AuditDto> audit(@QueryParam("limit") @DefaultValue("20") int limit) {
        return em.createQuery("select a from AuditEintrag a order by a.id desc", AuditEintrag.class)
                .setMaxResults(Math.min(limit, 200)).getResultList().stream()
                .map(a -> new AuditDto(a.getZeitpunkt(), a.getAktion(), a.getObjekt(), a.getDetails(),
                        a.getKorrelationsId(), a.getAkteur()))
                .toList();
    }
}
