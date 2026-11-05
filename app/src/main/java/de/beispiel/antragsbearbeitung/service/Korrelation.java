package de.beispiel.antragsbearbeitung.service;

import org.jboss.logging.MDC;

/**
 * Die Korrelations-ID steht im MDC des Logging-Frameworks. WildFly schreibt sie als Feld "mdc"
 * in jede JSON-Logzeile, Logstash macht daraus das Feld "korrelationsId". In Kibana findet eine
 * Suche so alle Zeilen zu einem Vorgang.
 */
public final class Korrelation {

    public static final String ID = "korrelationsId";
    public static final String AKTEUR = "akteur";

    private Korrelation() {
    }

    public static void setze(String korrelationsId) {
        MDC.put(ID, korrelationsId);
    }

    public static void setzeAkteur(String akteur) {
        MDC.put(AKTEUR, akteur);
    }

    public static String aktuell() {
        Object wert = MDC.get(ID);
        return wert != null ? wert.toString() : "-";
    }

    public static String akteur() {
        Object wert = MDC.get(AKTEUR);
        return wert != null ? wert.toString() : "system";
    }

    public static void aufraeumen() {
        MDC.remove(ID);
        MDC.remove(AKTEUR);
    }
}
