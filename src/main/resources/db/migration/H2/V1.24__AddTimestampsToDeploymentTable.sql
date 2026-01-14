-- Add columns as nullable first
alter table deployment
    add column created_at_ms bigint;
alter table deployment
    add column updated_at_ms bigint;

-- Populate existing rows with current timestamp as milliseconds since epoch
update deployment
set created_at_ms = EXTRACT(EPOCH FROM CURRENT_TIMESTAMP(3)) * 1000
where created_at_ms is null;
update deployment
set updated_at_ms = EXTRACT(EPOCH FROM CURRENT_TIMESTAMP(3)) * 1000
where updated_at_ms is null;

-- Make not nullable
alter table deployment
    alter column created_at_ms set not null;
alter table deployment
    alter column updated_at_ms set not null;
