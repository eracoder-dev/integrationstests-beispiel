package de.beispiel.antragsbearbeitung.prozess;

import de.beispiel.antragsbearbeitung.batch.BatchSteuerung;
import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

/** Service Task "Sollstellung starten": Camunda steuert den Ablauf, Jakarta Batch verarbeitet die Masse. */
@Dependent
@Named("batchStarten")
public class BatchStartenDelegate implements JavaDelegate {

    @Inject
    BatchSteuerung batch;

    @Override
    public void execute(DelegateExecution execution) {
        ProzessKontext.uebernehmen(execution);
        try {
            long id = batch.starten((String) execution.getVariable("monat"),
                    ((Number) execution.getVariable("failAfter")).intValue());
            execution.setVariable("ausfuehrungsId", id);
        } finally {
            Korrelation.aufraeumen();
        }
    }
}
