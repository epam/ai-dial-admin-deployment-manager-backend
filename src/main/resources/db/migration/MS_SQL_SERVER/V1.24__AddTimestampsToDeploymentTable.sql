-- Add columns as nullable first
alter table deployment
    add created_at_ms bigint;
alter table deployment
    add updated_at_ms bigint;
go

-- Populate existing rows with current timestamp as milliseconds since epoch
update deployment
set created_at_ms = DATEDIFF_BIG(millisecond, '1970-01-01 00:00:00.000', SYSDATETIMEOFFSET())
where created_at_ms is null;
update deployment
set updated_at_ms = DATEDIFF_BIG(millisecond, '1970-01-01 00:00:00.000', SYSDATETIMEOFFSET())
where updated_at_ms is null;
go

-- Make not nullable
alter table deployment
    alter column created_at_ms bigint not null;
alter table deployment
    alter column updated_at_ms bigint not null;
