package de.beispiel.antragsbearbeitung.betrieb;

import de.beispiel.antragsbearbeitung.prozess.ProzessMotor;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.health.Startup;

/**
 * Health Checks für Kubernetes oder einen Load Balancer. WildFly liefert sie auf dem Verwaltungsport:
 * /health/live, /health/ready, /health/started.
 *
 * <p>Der externe Adressdienst steht bewusst nicht in der Bereitschaft. Fällt er aus, fängt Fault Tolerance
 * das ab. Eine nicht bereite Anwendung würde stattdessen aus dem Load Balancer genommen, obwohl sie
 * weiter arbeiten kann.
 */
@ApplicationScoped
public class Gesundheitspruefungen {

    @Resource(lookup = "java:jboss/datasources/AntragsbearbeitungDS")
    DataSource datenquelle;

    @Inject
    ProzessMotor motor;

    @Produces
    @Liveness
    HealthCheck lebt() {
        return () -> HealthCheckResponse.up("anwendung");
    }

    @Produces
    @Readiness
    HealthCheck bereit() {
        return () -> {
            boolean datenbank = datenbankErreichbar();
            boolean camunda = motor.laeuft();
            return HealthCheckResponse.named("bereitschaft")
                    .status(datenbank && camunda)
                    .withData("datenbank", datenbank)
                    .withData("camunda", camunda)
                    .build();
        };
    }

    @Produces
    @Startup
    HealthCheck gestartet() {
        return () -> HealthCheckResponse.named("start").status(motor.laeuft()).build();
    }

    private boolean datenbankErreichbar() {
        try (Connection c = datenquelle.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("select 1")) {
            return r.next();
        } catch (Exception e) {
            return false;
        }
    }
}
