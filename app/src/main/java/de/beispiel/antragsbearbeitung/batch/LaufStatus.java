package de.beispiel.antragsbearbeitung.batch;

import java.util.Map;

/** Stand eines Batchlaufs, wie ihn die REST-Schnittstelle und Camunda sehen. */
public record LaufStatus(long ausfuehrungsId, String status, String exitStatus, Map<String, Long> zaehler) {
}
