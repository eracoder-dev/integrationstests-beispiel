package de.beispiel.antragsbearbeitung.batch;

/** Ein einzelner Datensatz ist fachlich unbrauchbar. Der Lauf überspringt ihn und macht weiter. */
public class FachlicherFehler extends RuntimeException {

    private final String vertragsnummer;

    public FachlicherFehler(String vertragsnummer, String grund) {
        super(grund);
        this.vertragsnummer = vertragsnummer;
    }

    public String getVertragsnummer() {
        return vertragsnummer;
    }
}
