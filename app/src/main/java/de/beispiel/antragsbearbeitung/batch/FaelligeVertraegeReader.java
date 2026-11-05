package de.beispiel.antragsbearbeitung.batch;

import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;

/**
 * Liest die aktiven Verträge sortiert nach Nummer und seitenweise. Gemerkt wird nur die letzte Nummer
 * ("Keyset-Paging"): Das bleibt bei großen Tabellen schnell und überlebt die Commits zwischen den Chunks,
 * anders als ein offener Datenbank-Cursor. Die letzte Nummer ist zugleich der Checkpoint.
 */
@Dependent
@Named("faelligeVertraegeReader")
public class FaelligeVertraegeReader extends AbstractItemReader {

    private static final int SEITENGROESSE = 500;

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    private String letzteNummer = "";
    private Iterator<VertragRef> seite = Collections.emptyIterator();

    @Override
    public void open(Serializable checkpoint) {
        // Beim Neustart steht hier die Nummer aus dem letzten Commit, sonst null.
        letzteNummer = checkpoint == null ? "" : (String) checkpoint;
    }

    @Override
    public Object readItem() {
        if (!seite.hasNext()) {
            seite = em.createQuery(
                            "select new de.beispiel.antragsbearbeitung.batch.VertragRef(v.nummer, v.monatsbeitrag) "
                                    + "from Vertrag v where v.status = 'AKTIV' and v.nummer > :letzte order by v.nummer",
                            VertragRef.class)
                    .setParameter("letzte", letzteNummer)
                    .setMaxResults(SEITENGROESSE)
                    .getResultList().iterator();
            if (!seite.hasNext()) {
                return null;
            }
        }
        VertragRef vertrag = seite.next();
        letzteNummer = vertrag.nummer();
        return vertrag;
    }

    @Override
    public Serializable checkpointInfo() {
        return letzteNummer;
    }
}
