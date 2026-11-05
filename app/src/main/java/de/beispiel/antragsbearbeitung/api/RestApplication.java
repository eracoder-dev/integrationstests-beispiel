package de.beispiel.antragsbearbeitung.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

@ApplicationPath("/api")
@OpenAPIDefinition(info = @Info(title = "Antragsbearbeitung Beispielanwendung", version = "1.0.0",
        description = "Anträge mit Camunda, Sollstellung mit Jakarta Batch, Adressprüfung mit Fault Tolerance"))
public class RestApplication extends Application {
}
