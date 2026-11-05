package de.beispiel.antragsbearbeitung.prozess;

import de.beispiel.antragsbearbeitung.service.Korrelation;
import org.camunda.bpm.engine.delegate.DelegateExecution;

/**
 * Asynchrone Schritte laufen im Job Executor und nicht mehr im Thread des REST-Aufrufs. Korrelations-ID und
 * Benutzer reisen deshalb als Prozessvariablen mit und kommen hier wieder ins Logging.
 */
final class ProzessKontext {

    private ProzessKontext() {
    }

    static void uebernehmen(DelegateExecution execution) {
        Korrelation.setze((String) execution.getVariable(Korrelation.ID));
        Object akteur = execution.getVariable(Korrelation.AKTEUR);
        if (akteur != null) {
            Korrelation.setzeAkteur(akteur.toString());
        }
    }
}
