CREATE TABLE IF NOT EXISTS digipost_jobb_data
(
    orgnr                      ORGNR     NOT NULL PRIMARY KEY,
    direct_job_reference       BIGINT,
    signer_url                 TEXT,
    status_query_token         TEXT
);
