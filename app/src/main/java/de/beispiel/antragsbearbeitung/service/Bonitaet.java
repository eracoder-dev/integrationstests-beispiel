package de.beispiel.antragsbearbeitung.service;

/** Einschätzung der Bonität. Die Grenzen sind unsere eigene Regel, nicht die des Auskunftsdienstes. */
public enum Bonitaet {
    GUT, MITTEL, SCHLECHT,
    /** Der Dienst war nicht erreichbar oder hat einen Fehler gemeldet. */
    UNBEKANNT;

    public static Bonitaet ausScore(int score) {
        if (score >= 600) {
            return GUT;
        }
        if (score >= 400) {
            return MITTEL;
        }
        return SCHLECHT;
    }
}
