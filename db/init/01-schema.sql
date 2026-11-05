-- Schema und Beispieldaten. Camunda legt seine eigenen Tabellen (ACT_*) beim Start selbst an.

CREATE TABLE vertrag (
    nummer               varchar(20)   PRIMARY KEY,
    vertragspartner  varchar(100)  NOT NULL,
    monatsbeitrag        numeric(10,2) NOT NULL,
    status               varchar(20)   NOT NULL,
    version              bigint        NOT NULL DEFAULT 0
);

CREATE INDEX idx_vertrag_status_nummer ON vertrag (status, nummer);

-- Pro Vertrag und Monat darf es nur eine Forderung geben. Der eindeutige Schlüssel sichert
-- den Batchlauf ab: Ein Neustart kann nichts doppelt buchen.
CREATE TABLE forderung (
    id              bigserial     PRIMARY KEY,
    vertrag_nummer  varchar(20)   NOT NULL REFERENCES vertrag (nummer),
    monat           varchar(7)    NOT NULL,
    betrag          numeric(10,2) NOT NULL,
    erzeugt_am      timestamp     NOT NULL DEFAULT now(),
    CONSTRAINT uq_forderung_vertrag_monat UNIQUE (vertrag_nummer, monat)
);

CREATE TABLE antrag (
    id                  bigserial     PRIMARY KEY,
    antragsnummer       varchar(20)   UNIQUE,
    name                varchar(100)  NOT NULL,
    plz                 varchar(5)    NOT NULL,
    ort                 varchar(100)  NOT NULL,
    antragssumme  numeric(12,2) NOT NULL,
    status              varchar(30)   NOT NULL,
    prozess_instanz_id  varchar(64),
    erfasst_am          timestamp     NOT NULL DEFAULT now(),
    version             bigint        NOT NULL DEFAULT 0
);

-- Nachvollziehbarkeit von Entscheidungen und Läufen (siehe DORA.md)
CREATE TABLE audit (
    id               bigserial    PRIMARY KEY,
    zeitpunkt        timestamp    NOT NULL DEFAULT now(),
    aktion           varchar(60)  NOT NULL,
    objekt           varchar(100) NOT NULL,
    details          text,
    korrelations_id  varchar(64),
    akteur           varchar(60)  NOT NULL
);

-- 20.000 Verträge, jeder fünfzigste ruht und wird vom Lauf übersprungen.
INSERT INTO vertrag (nummer, vertragspartner, monatsbeitrag, status)
SELECT 'V' || lpad(g::text, 7, '0'),
       'Kunde ' || g,
       round((20 + random() * 180)::numeric, 2),
       CASE WHEN g % 50 = 0 THEN 'RUHEND' ELSE 'AKTIV' END
FROM generate_series(1, 20000) AS g;

-- Ein paar absichtlich kaputte Datensätze: Der Lauf überspringt sie und protokolliert sie.
UPDATE vertrag SET monatsbeitrag = -1.00 WHERE nummer IN ('V0000777', 'V0004711', 'V0013337');
-- Beitragsfreie Verträge ergeben keine Forderung.
UPDATE vertrag SET monatsbeitrag = 0.00 WHERE nummer IN ('V0000101', 'V0000201', 'V0000301');
