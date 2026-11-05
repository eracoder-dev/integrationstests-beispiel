package de.beispiel.antragsbearbeitung.prozess;

import de.beispiel.antragsbearbeitung.domain.Antrag;
import de.beispiel.antragsbearbeitung.service.AdresseUngueltigException;
import de.beispiel.antragsbearbeitung.service.AdressErgebnis;
import de.beispiel.antragsbearbeitung.service.Adresspruefung;
import de.beispiel.antragsbearbeitung.service.AuditService;
import de.beispiel.antragsbearbeitung.service.Bonitaet;
import de.beispiel.antragsbearbeitung.service.Bonitaetspruefung;
import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service Task "Risiko prüfen". Im Modell steht asyncBefore: Der Schritt läuft im Job Executor und
 * nicht im REST-Aufruf, der den Antrag erfasst. Der Aufrufer wartet so nicht auf den Adressdienst.
 */
@Dependent
@Named("risikopruefung")
public class RisikopruefungDelegate implements JavaDelegate {

    private static final Logger LOG = Logger.getLogger(RisikopruefungDelegate.class);

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @Inject
    Adresspruefung adresse;

    @Inject
    Bonitaetspruefung bonitaet;

    @Inject
    AuditService audit;

    @Inject
    @ConfigProperty(name = "antrag.automatik.grenze", defaultValue = "300000")
    BigDecimal grenze;

    @Override
    public void execute(DelegateExecution execution) {
        ProzessKontext.uebernehmen(execution);
        try {
            Antrag antrag = em.find(Antrag.class, (Long) execution.getVariable("antragId"));
            AdressErgebnis ergebnis;
            try {
                ergebnis = adresse.pruefe(antrag.getPlz(), antrag.getOrt());
            } catch (AdresseUngueltigException e) {
                // Fachliches Ergebnis: Das Modell fängt es mit einem Fehler-Grenzereignis ab.
                execution.setVariable("ablehnungsgrund", "Adresse ungültig: " + e.getMessage());
                throw new BpmnError("ADRESSE_UNGUELTIG", e.getMessage());
            }
            // Zweiter Aufruf nach außen, diesmal per SOAP. Bei einer ungültigen Adresse kommt er gar nicht erst.
            Bonitaet bonitaetsurteil = bonitaet.pruefe(antrag.getName(), antrag.getPlz(), antrag.getAntragssumme());
            boolean automatisch = AntragsRegel.automatischAnnehmen(ergebnis, bonitaetsurteil, antrag.getAntragssumme(), grenze);
            execution.setVariable("adresse", ergebnis.name());
            execution.setVariable("bonitaet", bonitaetsurteil.name());
            execution.setVariable("automatisch", automatisch);
            antrag.setStatus(automatisch ? "GEPRUEFT" : "MANUELLE_PRUEFUNG");
            LOG.infof("Antrag %s geprüft: Adresse %s, Bonität %s, automatisch %s",
                    antrag.getAntragsnummer(), ergebnis, bonitaetsurteil, automatisch);
        } finally {
            Korrelation.aufraeumen();
        }
    }
}
