package de.beispiel.antragsbearbeitung.batch;

import de.beispiel.antragsbearbeitung.service.AuditService;
import jakarta.batch.api.listener.AbstractJobListener;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/** Hält Beginn und Ende jedes Laufs fest, damit am Morgen jemand nachsehen kann, ob alles gelaufen ist. */
@Dependent
@Named("sollstellungListener")
public class SollstellungListener extends AbstractJobListener {

    private static final Logger LOG = Logger.getLogger(SollstellungListener.class);

    @Inject
    JobContext job;

    @Inject
    AuditService audit;

    @Override
    public void beforeJob() {
        LOG.infof("Sollstellung gestartet, Ausführung %d", job.getExecutionId());
        audit.protokolliere("SOLLSTELLUNG_GESTARTET", "ausfuehrung-" + job.getExecutionId(), "");
    }

    @Override
    public void afterJob() {
        LOG.infof("Sollstellung beendet, Ausführung %d, Status %s", job.getExecutionId(), job.getBatchStatus());
        audit.protokolliere("SOLLSTELLUNG_BEENDET", "ausfuehrung-" + job.getExecutionId(), String.valueOf(job.getBatchStatus()));
    }
}
