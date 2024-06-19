create table publisering (
    uuid uuid primary key,
    orgnr varchar(9) not null,
    retry_count integer not null,
    avtale_uuid uuid references avtale_v1(uuid),
    avtalemal_uuid uuid references avtalemal(uuid)
);
