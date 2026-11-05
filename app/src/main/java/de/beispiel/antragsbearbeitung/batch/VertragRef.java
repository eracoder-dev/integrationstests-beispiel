package de.beispiel.antragsbearbeitung.batch;

import java.io.Serializable;
import java.math.BigDecimal;

/** Schlanke Sicht auf einen Vertrag für den Lauf, ohne Entität und ohne Persistenzkontext. */
public record VertragRef(String nummer, BigDecimal monatsbeitrag) implements Serializable {
}
