package de.beispiel.antragsbearbeitung.batch;

import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.JobExecution;
import jakarta.batch.runtime.Metric;
import jakarta.batch.runtime.StepExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.jboss.logging.Logger;

/**
 * Starten, Beobachten und Neustarten von Läufen.
 *
 * <p>Alle Methoden laufen bewusst ohne Transaktion des Aufrufers (NOT_SUPPORTED). Das Job-Repository
 * schreibt den Stand des Laufs in dieselbe Datenbank. Läge der Start in der Transaktion des Aufrufers,
 * wäre die Zeile für den Lauf noch nicht festgeschrieben, während er schon arbeitet, und
 * Statusänderungen gingen ins Leere.
 */
@ApplicationScoped
public class BatchSteuerung {

    private static final Logger LOG = Logger.getLogger(BatchSteuerung.class);
    private static final String JOB = "sollstellung";

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public long starten(String monat, int failAfter) {
        Properties parameter = new Properties();
        parameter.setProperty("monat", monat);
        parameter.setProperty("failAfter", String.valueOf(failAfter));
        long id = operator().start(JOB, parameter);
        LOG.infof("Sollstellung für %s gestartet, Ausführung %d", monat, id);
        return id;
    }

    /** Ein Neustart setzt beim letzten Checkpoint des abgebrochenen Laufs fort. */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public long neustarten(long ausfuehrungsId) {
        Properties parameter = new Properties();
        parameter.setProperty("monat", operator().getParameters(ausfuehrungsId).getProperty("monat"));
        parameter.setProperty("failAfter", "0");
        long neu = operator().restart(ausfuehrungsId, parameter);
        LOG.infof("Ausführung %d neu gestartet als %d", ausfuehrungsId, neu);
        return neu;
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public LaufStatus status(long ausfuehrungsId) {
        JobOperator operator = operator();
        JobExecution lauf = operator.getJobExecution(ausfuehrungsId);
        Map<String, Long> zaehler = new TreeMap<>();
        for (StepExecution schritt : operator.getStepExecutions(ausfuehrungsId)) {
            for (Metric metrik : schritt.getMetrics()) {
                zaehler.merge(metrik.getType().name(), metrik.getValue(), Long::sum);
            }
        }
        return new LaufStatus(ausfuehrungsId, lauf.getBatchStatus().name(), lauf.getExitStatus(), zaehler);
    }

    private JobOperator operator() {
        return BatchRuntime.getJobOperator();
    }
}
