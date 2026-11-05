package de.beispiel.antragsbearbeitung.service;

import de.beispiel.antragsbearbeitung.domain.AuditEintrag;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Hält fest, wer wann was entschieden oder gestartet hat. Das ist die Nachvollziehbarkeit, die
 * DORA für Änderungen verlangt. Der Eintrag läuft in einer eigenen Transaktion und bleibt
 * deshalb erhalten, auch wenn die aufrufende Transaktion zurückgerollt wird.
 */
@ApplicationScoped
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class);

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void protokolliere(String aktion, String objekt, String details) {
        em.persist(new AuditEintrag(aktion, objekt, details, Korrelation.aktuell(), Korrelation.akteur()));
        LOG.infof("Audit: %s %s %s", aktion, objekt, details);
    }
}
