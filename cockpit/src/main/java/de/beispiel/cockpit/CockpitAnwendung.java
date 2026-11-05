package de.beispiel.cockpit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Startet Camunda Cockpit, Tasklist und Admin auf der Datenbank der Beispielanwendung.
 *
 * <p>Die Anwendung selbst führt Prozesse und Jobs aus. Diese Engine hier ist nur das Fenster dazu:
 * Ihr Job Executor ist abgeschaltet, sie legt keine Tabellen an und deployt keine Modelle.
 */
@SpringBootApplication
public class CockpitAnwendung {

    public static void main(String[] args) {
        SpringApplication.run(CockpitAnwendung.class, args);
    }
}
