package de.beispiel.antragsbearbeitung.service;

/** Fachliches Ergebnis des Adressdienstes. Kein Ausfall, deshalb weder wiederholen noch als Fehler zählen. */
public class AdresseUngueltigException extends RuntimeException {
    public AdresseUngueltigException(String grund) {
        super(grund);
    }
}
