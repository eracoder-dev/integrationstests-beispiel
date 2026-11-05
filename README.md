# Beispielanwendung „Antragsbearbeitung“

[![CI](https://github.com/eracoder-dev/integrationstests-beispiel/actions/workflows/ci.yml/badge.svg)](https://github.com/eracoder-dev/integrationstests-beispiel/actions/workflows/ci.yml)

Eine kleine, vollständig lauffähige Fachanwendung als Übungsfeld: Man kann jede Technik anfassen, ausfallen lassen und im Log wiederfinden.

**Für die Integrationstests relevant:** `app/src/test/` mit dem Integrationstest `AntragIT`, der Testumgebung `TestStack` und dem Test der Stub-Antworten gegen die WSDL (`SoapVertragTest`), dazu `wiremock/mappings/` (die Fremdsysteme) und `.gitlab-ci.yml` (die Pipeline). Was geprüft ist und was nicht, steht in den Abschnitten „Tests“ und „GitLab-Pipeline“. Die Pipeline gibt es in zwei Varianten: Branch `main` (Docker-Executor) und Branch `gitlab-kubernetes-runner` (Kubernetes-Executor, Docker im Pod). Zusätzlich gibt es einen Workflow für GitHub Actions (`.github/workflows/ci.yml`): Dort ist kein Docker-in-Docker nötig, weil der Runner Docker mitbringt.

**Was sie ist:** eine Java-Anwendung (Jakarta EE 10) auf WildFly mit Camunda, Jakarta Batch, PostgreSQL, ELK und Monitoring. Zwei Fremdsysteme, ein REST-Adressdienst und eine SOAP-Bonitätsauskunft, simuliert WireMock. Dazu gehören Integrationstests mit Testcontainers und eine Pipeline für GitLab.

## Was drin ist

| Baustein | Umsetzung |
|---|---|
| Anwendungsserver | WildFly 39 mit dem Profil `standalone-microprofile.xml` |
| Jakarta EE | **Version 10**, wie bei JBoss EAP 8. WildFly 39 setzt sie um, die Anwendung wird gegen die EE-10-API gebaut. Genutzt werden CDI, JPA mit Hibernate, JAX-RS, Bean Validation, JTA und XML Web Services |
| MicroProfile | Config, Fault Tolerance, Health, OpenAPI |
| Prozesse | Camunda 7.24, eingebettet in die WAR, gemeinsame JTA-Transaktion mit JPA. Cockpit läuft als eigener kleiner Spring-Boot-Dienst auf derselben Datenbank |
| Massenverarbeitung | Jakarta Batch (JBeret), Job-Repository in der Datenbank |
| Datenbank | PostgreSQL 17 im Container |
| Externe Dienste | WireMock als REST-Adressdienst (steuerbar über die Postleitzahl) und als SOAP-Bonitätsauskunft (steuerbar über den Namen). Der SOAP-Client wird aus einer WSDL erzeugt |
| Logging | JSON-Logs von WildFly → Filebeat → Logstash → Elasticsearch → Kibana |
| Monitoring | Prometheus holt die Metriken ab, der Blackbox Exporter prüft die Health Checks, Grafana zeigt beides, drei Alarmregeln |

**ELK ist hier kostenlos.** Elasticsearch, Logstash und Kibana laufen mit der Basislizenz, die für alles in dieser Demo reicht. Die Sicherheitsfunktionen sind abgeschaltet, deshalb zeigt Kibana den Hinweis „Your data is not secure“. Für eine lokale Demo ist das so gewollt, für alles andere nicht.

## Starten

Voraussetzung: Docker Desktop mit etwa 6 GB Arbeitsspeicher. Java und Maven brauchst du nicht, gebaut wird im Container.

```bash
docker compose up -d --build
```

Der erste Start dauert einige Minuten (Images laden, Anwendung bauen). Danach:

| Was | Adresse |
|---|---|
| Bedienseite mit Aufgabenliste und Detailfenster | http://localhost:8080 |
| OpenAPI | http://localhost:8080/openapi |
| Health | http://localhost:9990/health |
| Metriken (Rohdaten) | http://localhost:9990/metrics |
| Grafana Dashboard | http://localhost:3030 (ohne Anmeldung) |
| Prometheus, Alarme | http://localhost:9090/alerts |
| Kibana | http://localhost:5601/app/discover |
| Camunda Cockpit, Tasklist, Admin | http://localhost:8083/camunda/app/cockpit/default/ (Anmeldung: siehe `cockpit/src/main/resources/application.yaml`) |
| Adressdienst (WireMock) | http://localhost:8089/__admin/mappings |
| Datenbank | `localhost:5432`, Benutzer, Kennwort und Datenbank `antragsbearbeitung` |

Beenden: `docker compose stop` (Daten bleiben), `docker compose down -v` (alles löschen).

Die Beispiele unten sind für eine Bash (Git Bash) geschrieben. In PowerShell heißt `curl` anders, dort `curl.exe` nehmen oder die Bedienseite benutzen.

## Zum Ausprobieren

Die Beispiele zeigen, wie die Fremdsysteme gesteuert werden, die die Integrationstests nachbilden.

### 1. Ein Antrag mit Camunda

Der Prozess `antrag` (Datei `app/src/main/resources/bpmn/antrag.bpmn`) prüft die Adresse, nimmt kleine Anträge automatisch an und schickt die übrigen in eine manuelle Prüfung.

```bash
# Summe über der Grenze von 300.000: geht in die manuelle Prüfung
curl -s -X POST localhost:8080/api/antraege -H 'Content-Type: application/json' \
  -d '{"name":"Erika Mustermann","plz":"80331","ort":"Muenchen","antragssumme":500000}'

curl -s localhost:8080/api/aufgaben                       # offene Aufgabe suchen, die id merken
curl -s -X POST localhost:8080/api/aufgaben/<id>/abschluss -H 'Content-Type: application/json' \
  -d '{"entscheidung":"annehmen"}'
curl -s localhost:8080/api/antraege/A00000001             # Status: POLICIERT
```

Der Aufruf kehrt sofort mit `202` zurück. Der erste Prozessschritt ist asynchron (`asyncBefore`) und läuft im Job Executor, damit der Aufrufer nicht auf den Adressdienst wartet.

### 2. Fault Tolerance am Adressdienst

Die Postleitzahl steuert das Verhalten des Adressdienstes:

| PLZ | Verhalten | Was die Anwendung tut |
|---|---|---|
| `80331` (und alle anderen) | antwortet in 80 ms | Adresse gültig |
| `11111` | „Adresse ungültig“ | fachlicher Fehler: nicht wiederholt, nicht als Ausfall gezählt, Antrag wird abgelehnt |
| `00000` | Status 503 | Retry, dann Circuit Breaker, dann Fallback „offen“, Antrag geht in die manuelle Prüfung |
| `99999` | antwortet nach 6 s | Zeitlimit, Retry, Fallback |
| `22222` | jeder zweite Aufruf fällt aus | Retry fängt es ab, der Aufrufer merkt nichts |

```bash
curl -s "localhost:8080/api/adresse/pruefen?plz=00000"
```

Die Werte stehen in `Adresspruefung.java` und lassen sich in `microprofile-config.properties` oder per Umgebungsvariable überschreiben.

### 3. Die SOAP-Schnittstelle ausprobieren

Bei jedem Antrag fragt die Anwendung nach der Adresse (REST) und danach bei einer **Bonitätsauskunft** (SOAP). Nur bei guter Bonität, gültiger Adresse und Summe bis 300.000 € wird der Antrag automatisch angenommen. Der Name im Antrag bestimmt, wie der Stub antwortet:

| Name enthält | Antwort des SOAP-Stubs | Ergebnis |
|---|---|---|
| nichts Besonderes | Score 720, Bonität gut | Police, wenn der Rest passt |
| `Schlecht` | Score 310, Bonität schlecht | manuelle Prüfung |
| `Fehler` | SOAP-Fault mit Status 500 | ein Wiederholungsversuch, dann Fallback „unbekannt“, manuelle Prüfung |
| `Langsam` | Antwort nach 6 Sekunden | Zeitlimit, Fallback „unbekannt“, manuelle Prüfung |

```bash
curl -s -X POST localhost:8080/api/antraege -H 'Content-Type: application/json' \
  -d '{"name":"Max Schlechtzahler","plz":"80331","ort":"Muenchen","antragssumme":120000}'
```

Die Schnittstelle ist in `app/src/main/resources/wsdl/bonitaet.wsdl` beschrieben.

## Aufbau

```
beispielanwendung/
  docker-compose.yml          alle Dienste
  db/init/01-schema.sql       Schema und 20.000 Beispielverträge
  wiremock/mappings/          Verhalten der Fremdsysteme: Adressdienst nach PLZ, SOAP-Bonitätsauskunft nach Name
  .gitlab-ci.yml              Pipeline mit den Stufen test und integration
  elk/                        Filebeat, Logstash-Pipeline, Einrichtung der Data View in Kibana
  cockpit/                    Camunda Cockpit, Tasklist und Admin (Spring Boot) auf der Datenbank der Anwendung
  app/
    Dockerfile                baut die WAR und konfiguriert WildFly
    Dockerfile.integration    Server-Image für die Integrationstests, nimmt die fertige WAR
    wildfly/konfigurieren.cli Datenquelle, Batch-Subsystem, JSON-Logging
    src/main/java/.../
      api/                    REST: Anträge, Aufgaben, Batch, Verträge, Audit, Korrelations-ID
      domain/                 JPA-Entitäten
      service/                Adressprüfung mit Fault Tolerance, Antragsservice, Audit
      prozess/                Camunda-Start, Delegates, Regel
      batch/                  Reader, Processor, Writer, Listener, Steuerung
      betrieb/                Health Checks
    src/main/resources/       bpmn/, batch-jobs/, microprofile-config.properties, persistence.xml
    src/main/resources/wsdl/  Vertrag der SOAP-Schnittstelle, daraus entsteht der Client
    src/test/                 Unit-Tests (*Test) und Integrationstests (*IT)
```

## Tests

Zwei Arten, getrennt durch den Dateinamen:

| Art | Dateien | Laufen mit | Dauer | Braucht |
|---|---|---|---|---|
| **Unit-Tests** | `*Test` | `mvn test` | wenige Sekunden | nur Java |
| **Integrationstests** | `*IT` | `mvn verify` | etwa eine halbe Minute | Docker |

```bash
cd app
mvn test      # nur die schnellen Tests
mvn verify    # alles: baut die WAR, baut das Server-Image, startet die Container, führt die Integrationstests aus
```

Ohne lokales Java und Maven geht es auch im Container (nur Unit-Tests): `docker run --rm -v "$PWD:/bau" -v m2-cache:/root/.m2 -w /bau/app maven:3.9-eclipse-temurin-21 mvn -B test`

### Unit-Tests (25, ohne Server und ohne Mocks)

* `RegelTest`: die Fachregeln als statische Methoden, auch die Zuordnung von Score zu Bonität
* `ProzessTest`: beide BPMN-Modelle in einer Engine im Speicher, die Delegates sind kleine Fakes. Geprüft wird, wo ein Vorgang wartet und welchen Weg er nimmt.
* `SoapVertragTest`: prüft die Erfolgsantworten des Stubs (`src/test/resources/soap/`) gegen das Schema der WSDL im Projekt. Ändert sich die WSDL oder weicht der Stub ab, schlägt er an, lange bevor ein Container startet. Ob die WSDL im Projekt noch zum echten Dienst passt, prüft er nicht.

### Integrationstests (5, gegen die echte Anwendung)

`AntragIT` startet mit Testcontainers eine eigene Umgebung: PostgreSQL mit dem Schema der Demo, WireMock als Ersatz für beide Fremdsysteme und die **echte Anwendung in WildFly**, mit derselben Konfiguration wie in der Demo. Getestet wird von außen über HTTP.

| Test | Was er zeigt |
|---|---|
| gute Bonität | Antrag wird policiert. Der Stub hat genau einen SOAP-Aufruf mit richtiger PLZ, richtigem Namen und dem Header `SOAPAction` bekommen. Das Audit steht in der Datenbank. |
| schlechte Bonität | Antrag geht in die manuelle Prüfung, die Aufgabe erscheint in Camunda |
| SOAP-Fault | Der Antrag hängt nicht, geht an einen Menschen. Der Stub wurde genau zweimal gefragt (erster Versuch plus Wiederholung). |
| langsamer Dienst | Der REST-Aufruf kehrt in unter 3 Sekunden zurück. Das Zeitlimit greift, der Antrag geht an einen Menschen. |
| ungültige Adresse | Antrag wird abgelehnt, ohne dass der Bonitätsdienst überhaupt gefragt wird |

So lässt sich prüfen, ob die Tests etwas taugen: Baut man absichtlich einen Fehler in die Regel ein (Bonität wird ignoriert), sollten genau die Tests fehlschlagen, die die Bonität berühren.

Das Server-Image baut Maven vor den Tests per `docker build` (siehe `pom.xml`, `Dockerfile.integration`). Lässt man Testcontainers das Image selbst bauen, bleibt der Test ohne Fehlermeldung beim Hochladen der Dateien stehen. Aus einer IDE heraus muss man einmal `mvn verify -DskipTests` laufen lassen, damit das Image existiert.

## GitLab-Pipeline

Dieser Branch (`gitlab-kubernetes-runner`) enthält die Pipeline für einen Runner mit **Kubernetes-Executor**, bei dem Docker als Dienst im Pod des Jobs läuft. Der Branch `main` enthält die Variante für einen Runner mit Docker-Executor.

`.gitlab-ci.yml` hat zwei Stufen:

1. **`test`**: die Unit-Tests, ohne Docker, bei jedem Push
2. **`integration`**: `mvn verify` mit Docker-in-Docker. Der Dienst `docker:27-dind` läuft als weiterer Container im Pod des Jobs, beide teilen sich `localhost`. Die Stufe läuft nur auf dem Hauptzweig und in Merge Requests.

**Ohne GitLab und ohne Kubernetes nachstellen:** Die Befehle des Jobs `integration-tests` (Variablen, `before_script`, `script`) lassen sich in einem Maven-Container ausführen. Der privilegierte Docker-Dienst (`docker:27-dind`) läuft im selben Netzwerk-Namensraum wie dieser Container, so wie die Container eines Pods. Beide sprechen sich nur über `localhost` an.

**Was das zeigt und was nicht:** Es zeigt, dass die Befehle und der Zugang über `localhost` zusammenpassen. Gemessener Speicher beim Nachstellen unter Docker Desktop (alle 10 Sekunden): Spitze im Job-Container etwa 0,7 GiB, im Docker-Dienst mit den drei Testcontainern etwa 1,5 GiB. Es zeigt nicht, wie ein Kubernetes-Runner die Datei ausführt: `privileged = true`, die Freigabe der Ressourcen-Variablen, `HEALTHCHECK_TCP_PORT`, `rules`, Cache, Testberichte und das Laden der Images im Cluster sind nicht geprüft.

### Kubernetes-Executor: Docker im Pod

Was der Betreiber des Runners einstellen muss. Das folgt der GitLab-Dokumentation zum Kubernetes-Executor und ist in keinem Cluster erprobt:

* **Privilegierte Container.** Docker-in-Docker braucht `privileged = true` in der Runner-Konfiguration (`[runners.kubernetes]`). Die Doku warnt, dass dabei der Kernel des Knotens für die Job-Container erreichbar werden kann, und empfiehlt, solche Jobs mit einem `node_selector` auf eigene Knoten zu legen.
* **Ressourcen per Variable.** `KUBERNETES_MEMORY_REQUEST` und `KUBERNETES_MEMORY_LIMIT` gelten für den Job-Container, am Dienst heißen sie `KUBERNETES_SERVICE_…`. Sie wirken nur, wenn der Betreiber die jeweilige `…_overwrite_max_allowed`-Einstellung setzt, für Dienste mit dem Präfix `service_`. Sonst gilt laut Doku: „If the maximum overwrite has not been set for a resource, the variable is not used.“ Der Speicher der Testcontainer zählt zum Docker-Dienst, nicht zum Job-Container.
* **`DOCKER_HOST`.** Job und Dienst teilen sich `localhost`. Neuere Runner erreichen Dienste laut Doku auch über ihren Namen (`docker`), ältere nur über `localhost`. Mit `localhost` geht beides.
* **Warten auf den Dienst.** `HEALTHCHECK_TCP_PORT` lässt den Runner warten, bis der Dienst antwortet. Zusätzlich wartet das `before_script` auf `docker info`.
* **Images.** Datenbank, Stub und Server-Image lädt der Docker-Dienst, nicht der Cluster. Docker-Hub-Grenzen und interne Registries gelten dort. Die Doku nennt einen Registry-Mirror für den dind-Dienst.

### OpenShift

Dieser Branch setzt privilegierte Container voraus. Läuft der Runner selbst in OpenShift (Kubernetes-Executor), gibt es dort meist keinen privilegierten Container und damit kein Docker-in-Docker. Dann bleiben drei Wege:

* **GitLab-Services:** Datenbank und Stub als Nachbar-Container (`services:`) starten, die Tests sprechen sie an. Das ersetzt Testcontainers.
* **Eigener Runner mit Docker** nur für die Integrationsstufe.
* **Kurzlebige Umgebung in OpenShift:** Die Pipeline deployt die Anwendung in ein Test-Projekt, testet gegen die Route und räumt danach auf.

Welcher Weg passt, hängt davon ab, wie der Runner im jeweiligen Betrieb läuft.

## Unterschiede zu einem echten Betrieb

* **Server und Datenbank:** WildFly statt JBoss EAP, PostgreSQL im Container. Die SQL-Schreibweise für „on conflict“ ist PostgreSQL-spezifisch.
* **Camunda:** läuft als Bibliothek in der WAR, das Cockpit als eigener Dienst auf derselben Datenbank.
* **Keine Sicherheit:** keine Anmeldung, keine Rollen, Elasticsearch, Kibana und Grafana ohne Passwort.
* **Keine Benachrichtigung bei Alarmen:** Die Alarmregeln gibt es nur in Prometheus, sie verschicken nichts.

Wie sich die Bausteine auf DORA beziehen, steht in [DORA.md](DORA.md).
