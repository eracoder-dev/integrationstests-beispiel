package de.beispiel.antragsbearbeitung.prozess;

import de.beispiel.antragsbearbeitung.domain.Antrag;
import de.beispiel.antragsbearbeitung.service.AuditService;
import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.jboss.logging.Logger;

/** Service Task "Police erstellen". Wiederholbar gebaut: Läuft er zweimal, bleibt das Ergebnis gleich. */
@Dependent
@Named("policierung")
public class PolicierungDelegate implements JavaDelegate {

    private static final Logger LOG = Logger.getLogger(PolicierungDelegate.class);

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @Inject
    AuditService audit;

    @Override
    public void execute(DelegateExecution execution) {
        ProzessKontext.uebernehmen(execution);
        try {
            Antrag antrag = em.find(Antrag.class, (Long) execution.getVariable("antragId"));
            if (!"POLICIERT".equals(antrag.getStatus())) {
                antrag.setStatus("POLICIERT");
                audit.protokolliere("ANTRAG_POLICIERT", antrag.getAntragsnummer(), "Police erstellt");
            }
            LOG.infof("Antrag %s policiert", antrag.getAntragsnummer());
        } finally {
            Korrelation.aufraeumen();
        }
    }
}
