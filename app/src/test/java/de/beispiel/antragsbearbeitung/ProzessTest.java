package de.beispiel.antragsbearbeitung;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prüft die beiden Prozessmodelle in einer Engine im Speicher. Die Delegates sind kleine Fakes, die
 * ihre Aufrufe festhalten. Getestet wird der Ablauf des Modells: wo der Vorgang wartet und welchen
 * Weg er nimmt, nicht die Fachlogik dahinter (die steckt in RegelTest).
 */
class ProzessTest {

    static ProcessEngine engine;
    static final List<String> aufrufe = new LinkedList<>();
    static final Queue<String> batchStatus = new LinkedList<>();

    @BeforeAll
    static void engineStarten() throws Exception {
        Map<Object, Object> beans = new HashMap<>();
        beans.put("risikopruefung", (JavaDelegate) ex -> {
            aufrufe.add("risiko");
            String plz = (String) ex.getVariable("plz");
            if ("11111".equals(plz)) {
                ex.setVariable("ablehnungsgrund", "Adresse ungültig");
                throw new BpmnError("ADRESSE_UNGUELTIG", "Adresse ungültig");
            }
            ex.setVariable("automatisch", "00001".equals(plz));
        });
        beans.put("policierung", (JavaDelegate) ex -> aufrufe.add("police"));
        beans.put("ablehnung", (JavaDelegate) ex -> aufrufe.add("ablehnung"));
        beans.put("batchStarten", (JavaDelegate) ex -> { aufrufe.add("batchStart"); ex.setVariable("ausfuehrungsId", 42L); });
        beans.put("statusAbfrage", (JavaDelegate) ex -> ex.setVariable("batchStatus", batchStatus.remove()));

        StandaloneInMemProcessEngineConfiguration konfiguration = new StandaloneInMemProcessEngineConfiguration();
        konfiguration.setJdbcUrl("jdbc:h2:mem:prozesstest;DB_CLOSE_DELAY=-1");
        konfiguration.setDatabaseSchemaUpdate("create-drop");
        konfiguration.setJobExecutorActivate(false);
        konfiguration.setBeans(beans);
        engine = konfiguration.buildProcessEngine();

        for (String modell : new String[] {"antrag.bpmn", "sollstellungslauf.bpmn"}) {
            try (InputStream s = ProzessTest.class.getResourceAsStream("/bpmn/" + modell)) {
                engine.getRepositoryService().createDeployment().addInputStream(modell, s).deploy();
            }
        }
    }

    @AfterAll
    static void engineBeenden() {
        engine.close();
    }

    @BeforeEach
    void zuruecksetzen() {
        aufrufe.clear();
        batchStatus.clear();
    }

    /** Der Schritt "Risiko prüfen" ist asynchron: Der Start kehrt zurück, der Job muss erst laufen. */
    private void ersteJobsAusfuehren(ProcessInstance instanz) {
        Job job = engine.getManagementService().createJobQuery().processInstanceId(instanz.getId()).singleResult();
        assertThat(job).as("asynchroner Job vor dem Risikoschritt").isNotNull();
        engine.getManagementService().executeJob(job.getId());
    }

    private ProcessInstance antragStarten(String plz) {
        return engine.getRuntimeService().startProcessInstanceByKey("antrag", Map.of("plz", plz));
    }

    @Test
    void startKehrtZurueckOhneDenRisikoschrittAuszufuehren() {
        antragStarten("00001");
        assertThat(aufrufe).isEmpty();
    }

    @Test
    void kleinerAntragWirdAutomatischPoliciert() {
        ProcessInstance instanz = antragStarten("00001");
        ersteJobsAusfuehren(instanz);

        assertThat(aufrufe).containsExactly("risiko", "police");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanz.getId()).count()).isZero();
    }

    @Test
    void sonstigerAntragWartetAufDieManuellePruefungUndWirdAngenommen() {
        ProcessInstance instanz = antragStarten("80331");
        ersteJobsAusfuehren(instanz);

        Task aufgabe = engine.getTaskService().createTaskQuery().processInstanceId(instanz.getId()).singleResult();
        assertThat(aufgabe.getTaskDefinitionKey()).isEqualTo("Manuell");
        assertThat(aufrufe).containsExactly("risiko");

        engine.getTaskService().complete(aufgabe.getId(), Map.of("entscheidung", "annehmen"));
        assertThat(aufrufe).containsExactly("risiko", "police");
    }

    @Test
    void manuelleAblehnungFuehrtZurAblehnung() {
        ProcessInstance instanz = antragStarten("80331");
        ersteJobsAusfuehren(instanz);
        Task aufgabe = engine.getTaskService().createTaskQuery().processInstanceId(instanz.getId()).singleResult();

        engine.getTaskService().complete(aufgabe.getId(), Map.of("entscheidung", "ablehnen"));
        assertThat(aufrufe).containsExactly("risiko", "ablehnung");
    }

    @Test
    void ungueltigeAdresseWirdOhneWiederholungAbgelehnt() {
        ProcessInstance instanz = antragStarten("11111");
        ersteJobsAusfuehren(instanz);

        // Fachlicher Fehler: Das Fehler-Grenzereignis fängt ihn, es gibt keinen Retry und keinen Incident.
        assertThat(aufrufe).containsExactly("risiko", "ablehnung");
        assertThat(engine.getRuntimeService().createIncidentQuery().count()).isZero();
    }

    @Test
    void sollstellungslaufWartetInDerSchleifeBisDerBatchBeendetIst() {
        batchStatus.addAll(List.of("STARTED", "STARTED", "COMPLETED"));
        ProcessInstance instanz = engine.getRuntimeService().startProcessInstanceByKey("sollstellungslauf",
                Map.of("monat", "2026-11", "failAfter", 0));
        ersteJobsAusfuehren(instanz);

        for (int runde = 1; runde <= 3; runde++) {
            Job timer = engine.getManagementService().createJobQuery().processInstanceId(instanz.getId()).timers().singleResult();
            assertThat(timer).as("Warten in Runde " + runde).isNotNull();
            engine.getManagementService().executeJob(timer.getId());
        }

        assertThat(aufrufe).containsExactly("batchStart");
        assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instanz.getId()).count()).isZero();
    }

    @Test
    void fehlgeschlagenerBatchlaufHoltEinenMenschen() {
        batchStatus.add("FAILED");
        ProcessInstance instanz = engine.getRuntimeService().startProcessInstanceByKey("sollstellungslauf",
                Map.of("monat", "2026-12", "failAfter", 0));
        ersteJobsAusfuehren(instanz);
        Job timer = engine.getManagementService().createJobQuery().processInstanceId(instanz.getId()).timers().singleResult();
        engine.getManagementService().executeJob(timer.getId());

        Task aufgabe = engine.getTaskService().createTaskQuery().processInstanceId(instanz.getId()).singleResult();
        assertThat(aufgabe.getTaskDefinitionKey()).isEqualTo("Fehler");
    }
}
