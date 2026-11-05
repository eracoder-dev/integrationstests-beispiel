package de.beispiel.antragsbearbeitung.batch;

import jakarta.batch.api.chunk.listener.SkipProcessListener;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

/** Jeder übersprungene Datensatz kommt mit Nummer und Grund ins Log. In Kibana lassen sie sich danach gesammelt auswerten. */
@Dependent
@Named("skipProtokoll")
public class SkipProtokoll implements SkipProcessListener {

    private static final Logger LOG = Logger.getLogger(SkipProtokoll.class);

    @Override
    public void onSkipProcessItem(Object item, Exception ex) {
        String nummer = item instanceof VertragRef v ? v.nummer() : String.valueOf(item);
        LOG.warnf("Vertrag %s übersprungen: %s", nummer, ex.getMessage());
    }
}
