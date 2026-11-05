package de.beispiel.antragsbearbeitung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integrationstests gegen die laufende Anwendung. Getestet wird, was Unit-Tests nicht sehen können: das
 * Zusammenspiel von REST, JPA, Camunda, Fault Tolerance und den beiden Fremdsystemen im echten Server.
 *
 * <p>Das SOAP-Fremdsystem ist ein WireMock-Stub. Jeder Test legt fest, wie der Stub antwortet, und prüft
 * danach zweierlei: was die Anwendung daraus macht und was sie dem Stub geschickt hat.
 */
class AntragIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @BeforeAll
    static void umgebungStarten() throws Exception {
        TestStack.starten();
    }

    @BeforeEach
    void stubZuruecksetzen() throws Exception {
        // Zurück auf das Grundverhalten aus wiremock/mappings, und das Protokoll der Aufrufe leeren.
        stubAdmin("POST", "/mappings/reset", null);
        stubAdmin("DELETE", "/requests", null);
    }

    // ---- die Tests -----------------------------------------------------------------------------------

    @Test
    void gutesBonitaetsurteilFuehrtBeiKleinerSummeZurPoliceUndWirdDemDienstKorrektGemeldet() throws Exception {
        soapStub(200, TestStack.lesen("soap/antwort-gut.xml"), 0);

        String nummer = antragStellen("Erika Mueller", "80331", 150000);

        warteAufStatus(nummer, "POLICIERT");
        // Der Dienst muss genau einmal gefragt worden sein, mit den richtigen Angaben im SOAP-Umschlag.
        assertThat(soapAufrufe("80331", "Erika Mueller")).isEqualTo(1);
        // Und die Entscheidung steht nachvollziehbar im Audit, in der Datenbank.
        assertThat(audit()).contains("\"aktion\":\"ANTRAG_POLICIERT\"").contains(nummer);
    }

    @Test
    void schlechtesBonitaetsurteilSchicktDenAntragInDieManuellePruefung() throws Exception {
        soapStub(200, TestStack.lesen("soap/antwort-schlecht.xml"), 0);

        String nummer = antragStellen("Max Schlechtzahler", "80331", 100000);

        warteAufStatus(nummer, "MANUELLE_PRUEFUNG");
        // Camunda hat dazu eine Aufgabe angelegt, die ein Mensch entscheiden muss.
        assertThat(offeneAufgaben()).contains(nummer);
    }

    @Test
    void einSoapFaultWirdWiederholtUndFuehrtDannInDieManuellePruefungStattZumFehler() throws Exception {
        soapStub(500, TestStack.lesen("soap/fault-server.xml"), 0);

        String nummer = antragStellen("Frieda Fehlerfall", "80331", 100000);

        // Fallback "unbekannt": Der Antrag geht zu einem Menschen und bleibt nicht hängen.
        warteAufStatus(nummer, "MANUELLE_PRUEFUNG");
        // Retry: erster Versuch plus eine Wiederholung, also zwei Aufrufe beim Dienst.
        assertThat(soapAufrufe("80331", "Frieda Fehlerfall")).isEqualTo(2);
        assertThat(TestStack.logDerAnwendung()).contains("Bonitätsauskunft für PLZ 80331 nicht möglich");
    }

    @Test
    void einLangsamerDienstBlockiertWederDenAufrufNochDenProzess() throws Exception {
        soapStub(200, TestStack.lesen("soap/antwort-gut.xml"), 6000);

        long start = System.nanoTime();
        String nummer = antragStellen("Lars Langsam", "80331", 100000);
        Duration dauerDesAufrufs = Duration.ofNanos(System.nanoTime() - start);

        // Der erste Prozessschritt ist asynchron: Der Aufrufer wartet nicht auf die Fremdsysteme.
        assertThat(dauerDesAufrufs).isLessThan(Duration.ofSeconds(3));
        // Das Lese-Zeitlimit greift, der Antrag landet nach dem Fallback bei einem Menschen.
        warteAufStatus(nummer, "MANUELLE_PRUEFUNG");
    }

    @Test
    void eineUngueltigeAdresseWirdAbgelehntOhneDenBonitaetsdienstZuFragen() throws Exception {
        // PLZ 11111 liefert der Adressdienst-Stub als fachlich ungültig (siehe wiremock/mappings).
        String nummer = antragStellen("Hans Falsch", "11111", 100000);

        warteAufStatus(nummer, "ABGELEHNT");
        assertThat(soapAufrufe("11111", "Hans Falsch")).as("Aufrufe beim Bonitätsdienst").isZero();
    }

    // ---- Hilfsmethoden -------------------------------------------------------------------------------

    private static String antragStellen(String name, String plz, int summe) throws Exception {
        ObjectNode antrag = JSON.createObjectNode().put("name", name).put("plz", plz).put("ort", "Muenchen").put("antragssumme", summe);
        var antwort = TestStack.post(TestStack.anwendungUrl() + "/api/antraege", antrag.toString());
        assertThat(antwort.statusCode()).as("Antwort beim Erfassen: " + antwort.body()).isEqualTo(202);
        return JSON.readTree(antwort.body()).get("antragsnummer").asText();
    }

    private static void warteAufStatus(String nummer, String erwartet) {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(status(nummer)).isEqualTo(erwartet));
    }

    private static String status(String nummer) throws Exception {
        return JSON.readTree(TestStack.get(TestStack.anwendungUrl() + "/api/antraege/" + nummer).body()).get("status").asText();
    }

    private static String offeneAufgaben() throws Exception {
        return TestStack.get(TestStack.anwendungUrl() + "/api/aufgaben").body();
    }

    private static String audit() throws Exception {
        return TestStack.get(TestStack.anwendungUrl() + "/api/audit?limit=50").body().replace(": ", ":");
    }

    /** Lässt den Stub für den SOAP-Dienst mit dieser Antwort reagieren. Die Priorität 1 schlägt das Grundverhalten. */
    private static void soapStub(int status, String body, int verzoegerungMs) throws Exception {
        ObjectNode antwort = JSON.createObjectNode().put("status", status).put("body", body);
        antwort.putObject("headers").put("Content-Type", "text/xml; charset=utf-8");
        if (verzoegerungMs > 0) {
            antwort.put("fixedDelayMilliseconds", verzoegerungMs);
        }
        ObjectNode mapping = JSON.createObjectNode().put("priority", 1);
        mapping.putObject("request").put("method", "POST").put("urlPath", "/soap/bonitaet");
        mapping.set("response", antwort);
        stubAdmin("POST", "/mappings", mapping.toString());
    }

    /**
     * Zählt die Aufrufe beim SOAP-Stub mit dieser PLZ und diesem Namen im Umschlag. Geprüft wird per XPath im
     * SOAP-Body und über den Header SOAPAction, nicht über den genauen Text: Die Präfixe im XML wählt der
     * Client, sie dürfen sich ändern.
     */
    private static int soapAufrufe(String plz, String name) throws Exception {
        ObjectNode muster = JSON.createObjectNode().put("method", "POST").put("urlPath", "/soap/bonitaet");
        muster.putObject("headers").putObject("SOAPAction").put("contains", "pruefeBonitaet");
        ArrayNode inhalt = muster.putArray("bodyPatterns");
        inhalt.addObject().put("matchesXPath", "//*[local-name()='plz'][text()='" + plz + "']");
        inhalt.addObject().put("matchesXPath", "//*[local-name()='name'][text()='" + name + "']");
        JsonNode antwort = JSON.readTree(stubAdmin("POST", "/requests/count", muster.toString()));
        return antwort.get("count").asInt();
    }

    private static String stubAdmin(String methode, String pfad, String body) throws Exception {
        HttpRequest.Builder anfrage = HttpRequest.newBuilder(URI.create(TestStack.stubAdminUrl() + pfad))
                .header("Content-Type", "application/json; charset=utf-8");
        anfrage = body == null
                ? anfrage.method(methode, HttpRequest.BodyPublishers.noBody())
                : anfrage.method(methode, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> antwort = HTTP.send(anfrage.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(antwort.statusCode()).as("WireMock " + methode + " " + pfad + ": " + antwort.body()).isBetween(200, 299);
        return antwort.body();
    }
}
