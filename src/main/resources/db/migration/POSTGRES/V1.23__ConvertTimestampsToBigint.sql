-- Convert image_definition timestamp columns to bigint
alter table image_definition
    add column created_at_ms bigint;
alter table image_definition
    add column updated_at_ms bigint;

-- Convert existing timestamp data to milliseconds since epoch
update image_definition
set created_at_ms = EXTRACT(EPOCH FROM created_at) * 1000;
update image_definition
set updated_at_ms = EXTRACT(EPOCH FROM updated_at) * 1000;

-- Drop old timestamp columns
alter table image_definition
    drop column created_at;
alter table image_definition
    drop column updated_at;

-- Make not nullable
alter table image_definition
    alter column created_at_ms set not null;
alter table image_definition
    alter column updated_at_ms set not null;

-- Convert built_image timestamp column to bigint
alter table built_image
    add column created_at_ms bigint;

-- Convert existing timestamp data to milliseconds since epoch
update built_image
set created_at_ms = EXTRACT(EPOCH FROM created_at) * 1000;

-- Drop old timestamp column
alter table built_image
    drop column created_at;

-- Make not nullable
alter table built_image
    alter column created_at_ms set not null;
