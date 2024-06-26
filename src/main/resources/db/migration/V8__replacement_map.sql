alter table avtalemal
    add column replacement_map jsonb not null default '{}'::jsonb;
