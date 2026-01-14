-- Convert image_definition timestamp columns to bigint
alter table image_definition
    add created_at_ms bigint;
alter table image_definition
    add updated_at_ms bigint;
go

-- Convert existing timestamp data to milliseconds since epoch
update image_definition
set created_at_ms = DATEDIFF_BIG(millisecond, '1970-01-01 00:00:00.000', created_at);
update image_definition
set updated_at_ms = DATEDIFF_BIG(millisecond, '1970-01-01 00:00:00.000', updated_at);
go

-- Drop old timestamp columns
alter table image_definition
    drop column created_at;
alter table image_definition
    drop column updated_at;
go

-- Make not nullable
alter table image_definition
    alter column created_at_ms bigint not null;
alter table image_definition
    alter column updated_at_ms bigint not null;
go

-- Convert built_image timestamp column to bigint
alter table built_image
    add created_at_ms bigint;
go

-- Convert existing timestamp data to milliseconds since epoch
update built_image
set created_at_ms = DATEDIFF_BIG(millisecond, '1970-01-01 00:00:00.000', created_at);
go

-- Drop old timestamp column
alter table built_image
    drop column created_at;
go

-- Make not nullable
alter table built_image
    alter column created_at_ms bigint not null;
