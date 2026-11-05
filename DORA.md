# DORA und diese Beispielanwendung

DORA ist eine EU-Verordnung für Finanzunternehmen. Sie verlangt vor allem Organisation und Nachweise, keinen bestimmten Code. Was Entwickler beitragen, sind Fähigkeiten der Anwendung, die diese Nachweise erst möglich machen. Diese Datei ordnet die Bausteine der Demo zu.

Die Fristen für schwere Vorfälle: Erstmeldung binnen 4 Stunden nach der Einstufung, spätestens 24 Stunden nachdem der Vorfall bekannt wurde, Zwischenbericht binnen 72 Stunden, Abschlussbericht binnen eines Monats.

| Anforderung aus DORA | Was die Anwendung dazu beiträgt | Wo es in der Demo steckt |
|---|---|---|
| Vorfälle schnell erkennen | Health Checks für Betrieb und Load Balancer, strukturierte Logs, Fehler sichtbar im Log | `betrieb/Gesundheitspruefungen.java`, `/health`, JSON-Logging in `konfigurieren.cli` |
| Vorfälle einordnen | Eine Suche zeigt, wie viele Vorgänge betroffen sind und seit wann | Korrelations-ID und Benutzer in jeder Logzeile, Kibana |
| Änderungen und Entscheidungen nachvollziehbar | Audit-Tabelle, die auch bei einem Rollback erhalten bleibt | `service/AuditService.java`, `GET /api/audit` |
| Ausfälle fremder Systeme überstehen | Zeitlimit, Retry, Circuit Breaker, Bulkhead und Fallback | `service/Adresspruefung.java`, Szenarien mit WireMock |
| Wiederanlauf nach einer Störung | Batchläufe, die beim Checkpoint weitermachen und nichts doppelt buchen | `batch/`, Job-Repository in der Datenbank |
| Konfiguration ohne Neubau | Grenzen und Zeitlimits per MicroProfile Config | `microprofile-config.properties`, Umgebungsvariablen |
| Dienstleister im Griff | Der externe Dienst ist hinter einer eigenen Klasse gekapselt, sein Ausfall ist getestet | `Adresspruefung`, `wiremock/mappings/` |
| Datenschutz im Log | Keine Namen der Antragsteller in den Logzeilen, Maskierung von IBAN-Mustern in der Pipeline. Postleitzahl und Ort stehen noch in zwei Logzeilen der Adressprüfung, bei echten Daten wäre das zu ändern | `elk/logstash/pipeline/logstash.conf`, `Adresspruefung.java` |

## Was die Demo nicht leistet

* **Keine Alarme.** Ein offener Circuit Breaker oder eine hohe Fehlerrate müsste in Kibana einen Alarm auslösen. Die Daten dafür sind da, die Regeln fehlen.
* **Keine Metriken zu Fault Tolerance.** Der Zustand der Circuit Breaker lässt sich nur über die Logs ablesen.
* **Keine Sicherheit.** Anmeldung, Rollen, Zugriffsprotokolle und Verschlüsselung fehlen vollständig.
* **Keine Tests des Wiederanlaufs im Ganzen.** Neustart eines Batchlaufs ist geprüft, ein Serverneustart mitten im Lauf nicht.
* **Keine Prüfung der Bibliotheken.** Ein Scanner für bekannte Schwachstellen und ein Überblick über die Dienstleister gehören zu DORA, liegen aber außerhalb des Codes.
