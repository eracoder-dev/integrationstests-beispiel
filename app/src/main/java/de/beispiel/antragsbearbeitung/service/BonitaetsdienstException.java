package de.beispiel.antragsbearbeitung.service;

/** Technischer Fehler beim Aufruf der SOAP-Schnittstelle: SOAP-Fault, Zeitüberschreitung, Verbindungsfehler. */
public class BonitaetsdienstException extends RuntimeException {
    public BonitaetsdienstException(String meldung, Throwable ursache) {
        super(meldung, ursache);
    }
}
