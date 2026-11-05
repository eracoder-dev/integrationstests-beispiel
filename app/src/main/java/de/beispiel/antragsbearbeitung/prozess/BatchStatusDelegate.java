package de.beispiel.antragsbearbeitung.prozess;

import de.beispiel.antragsbearbeitung.batch.BatchSteuerung;
import de.beispiel.antragsbearbeitung.batch.LaufStatus;
import de.beispiel.antragsbearbeitung.service.Korrelation;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

/** Service Task "Status prüfen": legt den Stand des Laufs als Prozessvariable ab, das Gateway entscheidet danach. */
@Dependent
@Named("statusAbfrage")
public class BatchStatusDelegate implements JavaDelegate {

    @Inject
    BatchSteuerung batch;

    @Override
    public void execute(DelegateExecution execution) {
        ProzessKontext.uebernehmen(execution);
        try {
            LaufStatus status = batch.status((Long) execution.getVariable("ausfuehrungsId"));
            execution.setVariable("batchStatus", status.status());
        } finally {
            Korrelation.aufraeumen();
        }
    }
}
