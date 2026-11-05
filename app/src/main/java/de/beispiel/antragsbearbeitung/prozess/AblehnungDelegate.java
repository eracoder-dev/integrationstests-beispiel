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

/** Service Task "Antrag ablehnen", erreichbar über die Fehler-Grenze und über die manuelle Entscheidung. */
@Dependent
@Named("ablehnung")
public class AblehnungDelegate implements JavaDelegate {

    private static final Logger LOG = Logger.getLogger(AblehnungDelegate.class);

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @Inject
    AuditService audit;

    @Override
    public void execute(DelegateExecution execution) {
        ProzessKontext.uebernehmen(execution);
        try {
            Antrag antrag = em.find(Antrag.class, (Long) execution.getVariable("antragId"));
            Object grund = execution.getVariable("ablehnungsgrund");
            antrag.setStatus("ABGELEHNT");
            audit.protokolliere("ANTRAG_ABGELEHNT", antrag.getAntragsnummer(),
                    grund != null ? grund.toString() : "Entscheidung der manuellen Prüfung");
            LOG.infof("Antrag %s abgelehnt", antrag.getAntragsnummer());
        } finally {
            Korrelation.aufraeumen();
        }
    }
}
