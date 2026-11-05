package de.beispiel.antragsbearbeitung.service;

/** Technischer Fehler beim Aufruf des Adressdienstes: Ausfall, Zeitüberschreitung, Verbindungsfehler. */
public class AdressdienstException extends RuntimeException {
    public AdressdienstException(String meldung, Throwable ursache) {
        super(meldung, ursache);
    }

    public AdressdienstException(String meldung) {
        super(meldung);
    }
}
