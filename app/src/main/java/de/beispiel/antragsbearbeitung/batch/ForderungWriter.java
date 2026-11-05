package de.beispiel.antragsbearbeitung.batch;

import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Schreibt die Forderungen idempotent: "on conflict do nothing" auf dem eindeutigen Schlüssel
 * (Vertrag, Monat). Läuft ein Chunk nach einem Abbruch noch einmal, entsteht nichts doppelt.
 *
 * <p>Zum Ausprobieren des Neustarts bricht der Writer mit dem Parameter failAfter=N nach N
 * Datensätzen ab. Der laufende Chunk wird zurückgerollt, alle vorherigen bleiben erhalten.
 */
@Dependent
@Named("forderungWriter")
public class ForderungWriter extends AbstractItemWriter {

    private static final Logger LOG = Logger.getLogger(ForderungWriter.class);

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @Inject
    @BatchProperty(name = "failAfter")
    String failAfter;

    private int verarbeitet;

    @Override
    public void writeItems(List<Object> items) {
        int abbruchNach = failAfter == null || failAfter.isBlank() ? 0 : Integer.parseInt(failAfter.trim());
        for (Object item : items) {
            ForderungsZeile zeile = (ForderungsZeile) item;
            em.createNativeQuery("insert into forderung (vertrag_nummer, monat, betrag) "
                            + "values (:nummer, :monat, :betrag) "
                            + "on conflict (vertrag_nummer, monat) do nothing")
                    .setParameter("nummer", zeile.vertragNummer())
                    .setParameter("monat", zeile.monat())
                    .setParameter("betrag", zeile.betrag())
                    .executeUpdate();
            verarbeitet++;
            if (abbruchNach > 0 && verarbeitet >= abbruchNach) {
                LOG.errorf("Simulierter Abbruch nach %d Datensätzen", verarbeitet);
                throw new IllegalStateException("Simulierter Abbruch nach " + verarbeitet + " Datensätzen");
            }
        }
    }
}
