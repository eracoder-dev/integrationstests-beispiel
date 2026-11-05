package de.beispiel.antragsbearbeitung.batch;

import java.io.Serializable;
import java.math.BigDecimal;

public record ForderungsZeile(String vertragNummer, String monat, BigDecimal betrag) implements Serializable {
}
