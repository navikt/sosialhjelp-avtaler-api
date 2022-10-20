CREATE DOMAIN orgnr AS CHAR(9);

CREATE TABLE IF NOT EXISTS avtale_v1
(
    orgnr          ORGNR     NOT NULL PRIMARY KEY,
    avtaleversjon  TEXT,
    opprettet      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
);
