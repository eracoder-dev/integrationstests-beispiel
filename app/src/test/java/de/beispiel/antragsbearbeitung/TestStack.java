package de.beispiel.antragsbearbeitung;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Startet die Umgebung für die Integrationstests einmal pro Testlauf: PostgreSQL, WireMock als Ersatz für
 * beide Fremdsysteme (REST-Adressdienst und SOAP-Bonitätsdienst) und die echte Anwendung in WildFly.
 * Alle Container hängen in einem eigenen Netz und finden sich über ihre Namen.
 *
 * <p>Die Anwendung läuft mit demselben Servertyp, derselben Konfiguration (konfigurieren.cli) und demselben
 * Datenbankschema wie in der Demo. Getestet wird von außen über HTTP, also so wie ein Aufrufer es sieht.
 */
final class TestStack {

    private static final Path APP = Path.of("").toAbsolutePath();
    private static final String IMAGE = "antragsbearbeitung-integrationstest:latest";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static Network netz;
    private static PostgreSQLContainer datenbank;
    private static GenericContainer<?> stub;
    private static GenericContainer<?> anwendung;

    private TestStack() {
    }

    static synchronized void starten() throws Exception {
        if (anwendung != null) {
            return;
        }
        netz = Network.newNetwork();

        // Dasselbe Schema wie in der Demo (db/init/01-schema.sql), mit den Beispielverträgen.
        datenbank = new PostgreSQLContainer("postgres:17")
                .withNetwork(netz).withNetworkAliases("postgres")
                .withDatabaseName("antragsbearbeitung").withUsername("antragsbearbeitung").withPassword("antragsbearbeitung")
                .withCopyFileToContainer(MountableFile.forHostPath(APP.resolve("../db/init/01-schema.sql")),
                        "/docker-entrypoint-initdb.d/01-schema.sql");

        // Die Mappings in wiremock/mappings gelten als Grundverhalten, die Tests legen darüber eigene Stubs.
        stub = new GenericContainer<>("wiremock/wiremock:3.13.1")
                .withNetwork(netz).withNetworkAliases("stub")
                .withExposedPorts(8080)
                .withCommand("--global-response-templating")
                .withCopyFileToContainer(MountableFile.forHostPath(APP.resolve("../wiremock/mappings")), "/home/wiremock/mappings")
                .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

        // Das Image baut "mvn verify" vor den Tests per "docker build" (siehe pom.xml und Dockerfile.integration).
        // Aus einer IDE heraus muss man "mvn verify -DskipTests" einmal selbst laufen lassen, sonst fehlt es hier.
        anwendung = new GenericContainer<>(IMAGE)
                .withNetwork(netz).withNetworkAliases("anwendung")
                .withEnv(Map.of(
                        "DB_HOST", "postgres",
                        "ADRESSDIENST_URL", "http://stub:8080",
                        "BONITAETSDIENST_URL", "http://stub:8080/soap/bonitaet",
                        "JAVA_OPTS", "-Xms256m -Xmx768m -Djava.net.preferIPv4Stack=true"))
                .withCommand("/opt/jboss/wildfly/bin/standalone.sh", "-c", "standalone-microprofile.xml", "-b", "0.0.0.0", "-bmanagement", "0.0.0.0")
                .withExposedPorts(8080, 9990)
                // Bereit heißt: Datenbank erreichbar und Camunda gestartet (eigener Health Check der Anwendung).
                .waitingFor(Wait.forHttp("/health/ready").forPort(9990).forStatusCode(200).withStartupTimeout(Duration.ofMinutes(4)));

        datenbank.start();
        stub.start();
        anwendung.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            anwendung.stop();
            stub.stop();
            datenbank.stop();
            netz.close();
        }));
    }

    static String anwendungUrl() {
        return "http://" + anwendung.getHost() + ":" + anwendung.getMappedPort(8080);
    }

    static String stubAdminUrl() {
        return "http://" + stub.getHost() + ":" + stub.getMappedPort(8080) + "/__admin";
    }

    static String logDerAnwendung() {
        return anwendung.getLogs();
    }

    // ---- kleine HTTP-Helfer ------------------------------------------------------------------------

    static HttpResponse<String> get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static HttpResponse<String> post(String url, String json) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static String lesen(String ressource) throws Exception {
        return Files.readString(Path.of("src/test/resources", ressource), StandardCharsets.UTF_8);
    }
}
