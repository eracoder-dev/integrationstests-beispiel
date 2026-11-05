package de.beispiel.antragsbearbeitung.batch;

import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.chunk.ItemProcessor;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.math.BigDecimal;

/** Der Processor rechnet nur und hat keine Nebenwirkungen. Die Regel steckt in einer statischen Methode, die sich ohne Container testen lässt. */
@Dependent
@Named("beitragsberechnung")
public class Beitragsberechnung implements ItemProcessor {

    @Inject
    @BatchProperty(name = "monat")
    String monat;

    @Override
    public Object processItem(Object item) {
        return berechne((VertragRef) item, monat);
    }

    /**
     * @return die Forderung, oder null für beitragsfreie Verträge: null heißt, der Datensatz geht nicht an den Writer
     * @throws FachlicherFehler bei einem negativen Beitrag
     */
    public static ForderungsZeile berechne(VertragRef vertrag, String monat) {
        BigDecimal beitrag = vertrag.monatsbeitrag();
        if (beitrag.signum() < 0) {
            throw new FachlicherFehler(vertrag.nummer(), "Negativer Monatsbeitrag " + beitrag);
        }
        if (beitrag.signum() == 0) {
            return null;
        }
        return new ForderungsZeile(vertrag.nummer(), monat, beitrag);
    }
}
