package de.beispiel.antragsbearbeitung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.beispiel.antragsbearbeitung.batch.Beitragsberechnung;
import de.beispiel.antragsbearbeitung.batch.FachlicherFehler;
import de.beispiel.antragsbearbeitung.batch.ForderungsZeile;
import de.beispiel.antragsbearbeitung.batch.VertragRef;
import de.beispiel.antragsbearbeitung.prozess.AntragsRegel;
import de.beispiel.antragsbearbeitung.service.AdressErgebnis;
import de.beispiel.antragsbearbeitung.service.Bonitaet;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Die Fachregeln stecken in statischen Methoden und laufen ohne Server, Datenbank oder Mocks. */
class RegelTest {

    private static final BigDecimal GRENZE = new BigDecimal("300000");

    @Test
    void automatischNurBeiBestaetigterAdresseGuterBonitaetUndKleinerSumme() {
        assertThat(AntragsRegel.automatischAnnehmen(AdressErgebnis.GUELTIG, Bonitaet.GUT, new BigDecimal("150000"), GRENZE)).isTrue();
        assertThat(AntragsRegel.automatischAnnehmen(AdressErgebnis.GUELTIG, Bonitaet.GUT, GRENZE, GRENZE)).isTrue();
        assertThat(AntragsRegel.automatischAnnehmen(AdressErgebnis.GUELTIG, Bonitaet.GUT, new BigDecimal("300000.01"), GRENZE)).isFalse();
    }

    @Test
    void offeneAdresseGehtImmerInDieManuellePruefung() {
        assertThat(AntragsRegel.automatischAnnehmen(AdressErgebnis.OFFEN, Bonitaet.GUT, new BigDecimal("1000"), GRENZE)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"MITTEL", "SCHLECHT", "UNBEKANNT"})
    void ohneGuteBonitaetEntscheidetEinMensch(Bonitaet bonitaet) {
        assertThat(AntragsRegel.automatischAnnehmen(AdressErgebnis.GUELTIG, bonitaet, new BigDecimal("1000"), GRENZE)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"720, GUT", "600, GUT", "599, MITTEL", "400, MITTEL", "399, SCHLECHT", "0, SCHLECHT"})
    void scoreWirdZurBonitaet(int score, Bonitaet erwartet) {
        assertThat(Bonitaet.ausScore(score)).isEqualTo(erwartet);
    }

    @Test
    void forderungFuerNormalenVertrag() {
        ForderungsZeile z = Beitragsberechnung.berechne(new VertragRef("V1", new BigDecimal("49.90")), "2026-11");
        assertThat(z).isEqualTo(new ForderungsZeile("V1", "2026-11", new BigDecimal("49.90")));
    }

    @Test
    void beitragsfreierVertragErgibtKeineForderung() {
        assertThat(Beitragsberechnung.berechne(new VertragRef("V2", BigDecimal.ZERO), "2026-11")).isNull();
    }

    @Test
    void negativerBeitragIstEinFachlicherFehlerMitVertragsnummer() {
        assertThatThrownBy(() -> Beitragsberechnung.berechne(new VertragRef("V3", new BigDecimal("-1.00")), "2026-11"))
                .isInstanceOfSatisfying(FachlicherFehler.class, e -> assertThat(e.getVertragsnummer()).isEqualTo("V3"));
    }
}
