package de.beispiel.antragsbearbeitung.service;

import de.beispiel.antragsbearbeitung.domain.Antrag;
import de.beispiel.antragsbearbeitung.prozess.ProzessMotor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;

@ApplicationScoped
public class AntragService {

    @PersistenceContext(unitName = "antragsbearbeitung")
    EntityManager em;

    @Inject
    ProzessMotor motor;

    @Inject
    AuditService audit;

    /**
     * Erfasst den Antrag und startet den Prozess. Beides läuft in einer Transaktion: Entweder gibt es
     * Antrag und Prozess, oder keins von beiden. Der erste Prozessschritt ist asynchron, deshalb
     * kehrt der Aufruf zurück, ohne auf den Adressdienst zu warten.
     */
    @Transactional
    public Antrag erfassen(String name, String plz, String ort, BigDecimal summe) {
        Antrag antrag = new Antrag(name, plz, ort, summe);
        em.persist(antrag);
        em.flush();
        antrag.setAntragsnummer(String.format("A%08d", antrag.getId()));

        ProcessInstance instanz = motor.runtime().startProcessInstanceByKey("antrag", antrag.getAntragsnummer(), Map.of(
                "antragId", antrag.getId(),
                "antragsnummer", antrag.getAntragsnummer(),
                // Die Korrelations-ID wandert als Variable mit, damit auch die asynchronen Schritte sie loggen.
                "korrelationsId", Korrelation.aktuell(),
                "akteur", Korrelation.akteur()));
        antrag.setProzessInstanzId(instanz.getId());
        audit.protokolliere("ANTRAG_ERFASST", antrag.getAntragsnummer(), "Summe " + summe);
        return antrag;
    }

    public Antrag finden(String antragsnummer) {
        return em.createQuery("select a from Antrag a where a.antragsnummer = :n", Antrag.class)
                .setParameter("n", antragsnummer).getResultStream().findFirst().orElse(null);
    }
}
