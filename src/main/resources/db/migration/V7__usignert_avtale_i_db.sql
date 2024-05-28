create table avtalemal
(
    uuid uuid primary key default uuid_generate_v4(),
    navn text not null,
    mal bytea,
    publisert timestamp
);

alter table avtale_v1
    add column avtalemal_uuid uuid references avtalemal (uuid);
