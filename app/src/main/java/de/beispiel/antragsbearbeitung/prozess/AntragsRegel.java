package de.beispiel.antragsbearbeitung.prozess;

import de.beispiel.antragsbearbeitung.service.AdressErgebnis;
import de.beispiel.antragsbearbeitung.service.Bonitaet;
import java.math.BigDecimal;

/** Die fachliche Regel, getrennt vom Ablauf: leicht ohne Container zu testen. */
public final class AntragsRegel {

    private AntragsRegel() {
    }

    /** Automatisch angenommen wird nur bei bestätigter Adresse, guter Bonität und Summe bis zur Grenze. */
    public static boolean automatischAnnehmen(AdressErgebnis adresse, Bonitaet bonitaet, BigDecimal summe, BigDecimal grenze) {
        return adresse == AdressErgebnis.GUELTIG
                && bonitaet == Bonitaet.GUT
                && summe.compareTo(grenze) <= 0;
    }
}
