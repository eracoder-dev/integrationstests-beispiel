package de.beispiel.antragsbearbeitung.prozess;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.JakartaTransactionProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobExecutor;
import org.jboss.logging.Logger;

/**
 * Startet Camunda 7 eingebettet in der Anwendung. Die Engine nutzt dieselbe Datenquelle und
 * dieselbe JTA-Transaktion wie JPA: Ein Antrag und sein Prozess werden gemeinsam festgeschrieben
 * oder gemeinsam zurückgerollt.
 *
 * <p>Der Job Executor von Camunda führt asynchrone Schritte und Timer aus. Er nutzt eigene Threads.
 * Für den Betrieb würde man ihn an den Managed Executor des Servers hängen.
 */
@ApplicationScoped
public class ProzessMotor {

    private static final Logger LOG = Logger.getLogger(ProzessMotor.class);
    private static final String[] MODELLE = {"antrag.bpmn", "sollstellungslauf.bpmn"};

    @Inject
    Instance<RisikopruefungDelegate> risikopruefung;
    @Inject
    Instance<PolicierungDelegate> policierung;
    @Inject
    Instance<AblehnungDelegate> ablehnung;
    @Inject
    Instance<BatchStartenDelegate> batchStarten;
    @Inject
    Instance<BatchStatusDelegate> batchStatus;

    private volatile ProcessEngine engine;

    void starten(@Observes @Initialized(ApplicationScoped.class) Object anwendungGestartet) {
        try {
            TransactionManager tm = (TransactionManager) new InitialContext().lookup("java:jboss/TransactionManager");

            DefaultJobExecutor jobExecutor = new DefaultJobExecutor();
            jobExecutor.setCorePoolSize(3);
            jobExecutor.setMaxPoolSize(6);
            jobExecutor.setQueueSize(6);
            jobExecutor.setMaxJobsPerAcquisition(3);
            jobExecutor.setWaitTimeInMillis(500);
            jobExecutor.setMaxWait(2000);

            // Die Delegates stehen im Modell als Ausdruck, etwa ${risikopruefung}.
            Map<Object, Object> beans = new HashMap<>();
            beans.put("risikopruefung", risikopruefung.get());
            beans.put("policierung", policierung.get());
            beans.put("ablehnung", ablehnung.get());
            beans.put("batchStarten", batchStarten.get());
            beans.put("statusAbfrage", batchStatus.get());   // nicht "batchStatus": Die Prozessvariable würde die Bean verdecken

            JakartaTransactionProcessEngineConfiguration konfiguration = new JakartaTransactionProcessEngineConfiguration();
            konfiguration.setTransactionManager(tm);
            konfiguration.setDataSourceJndiName("java:jboss/datasources/AntragsbearbeitungDS");
            konfiguration.setTransactionsExternallyManaged(true);
            konfiguration.setDatabaseSchemaUpdate("true");
            konfiguration.setHistory("audit");
            konfiguration.setProcessEngineName("antragsbearbeitung");
            konfiguration.setJobExecutor(jobExecutor);
            konfiguration.setJobExecutorActivate(true);
            konfiguration.setBeans(beans);
            engine = konfiguration.buildProcessEngine();

            for (String modell : MODELLE) {
                try (InputStream strom = getClass().getResourceAsStream("/bpmn/" + modell)) {
                    engine.getRepositoryService().createDeployment()
                            .name("antragsbearbeitung")
                            .enableDuplicateFiltering(true)
                            .addInputStream(modell, strom)
                            .deploy();
                }
            }
            LOG.infof("Camunda gestartet, %d Prozessdefinitionen",
                    engine.getRepositoryService().createProcessDefinitionQuery().latestVersion().count());
        } catch (NamingException | java.io.IOException e) {
            throw new IllegalStateException("Camunda konnte nicht gestartet werden", e);
        }
    }

    @PreDestroy
    void beenden() {
        if (engine != null) {
            engine.close();
        }
    }

    public boolean laeuft() {
        return engine != null
                && ((ProcessEngineConfigurationImpl) engine.getProcessEngineConfiguration()).getJobExecutor().isActive();
    }

    public RuntimeService runtime() {
        return engine.getRuntimeService();
    }

    public TaskService aufgaben() {
        return engine.getTaskService();
    }

    public HistoryService historie() {
        return engine.getHistoryService();
    }
}
