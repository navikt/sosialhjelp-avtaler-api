CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Fjerner gammel pk constraint og legger til ny uuid kolonne for ny pk
alter table avtale_v1
    drop constraint avtale_v1_pkey,
    add column uuid uuid not null default uuid_generate_v4(),
    add column navn text not null;

alter table digipost_jobb_data
    drop constraint digipost_jobb_data_pkey,
    add column uuid uuid not null default uuid_generate_v4();

-- Rename gammel index, men behold den for enklere oppslag av orgnr
create index avtale_v1_orgnr ON avtale_v1(orgnr);


-- Legg til ny pk constraint
alter table avtale_v1 add constraint avtale_v1_pkey primary key (uuid);
alter table digipost_jobb_data add constraint digipost_jobb_data_pkey primary key (uuid);

-- Sett samme uuid på avtaler og digipost_jobb_data hvor orgnr er lik
update digipost_jobb_data set uuid = (avtaler.uuid) from (select uuid, orgnr from avtale_v1) as avtaler where digipost_jobb_data.orgnr = avtaler.orgnr;

-- Sett navn på avtale_v1. Alle avtalene  i databasen på dette tidspunktet er like
update avtale_v1 set navn = 'Avtale for tilgjengeliggjøring av sentrale stønadsopplysninger til innsynsflate NKS';

-- Legg til ny fk constraint
alter table digipost_jobb_data
    add constraint digipost_jobb_data_avtale_v1_fk foreign key (uuid) references avtale_v1(uuid),
    drop column orgnr;
