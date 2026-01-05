-- Add version column to image_definition
alter table image_definition
    add column version varchar(255) not null default '1.0.0';

-- Add build-related columns to image_definition
alter table image_definition
    add column image_name varchar(255);
alter table image_definition
    add column build_status varchar(32);
alter table image_definition
    add column build_logs jsonb;
alter table image_definition
    add column built_at_ms bigint;

-- Migrate data from built_image to image_definition
update image_definition
set image_name = (
    select image_name from built_image where built_image.id = image_definition.last_built_image_id
),
build_status = (
    select status from built_image where built_image.id = image_definition.last_built_image_id
),
build_logs = (
    select logs from built_image where built_image.id = image_definition.last_built_image_id
),
built_at_ms = (
    select created_at_ms from built_image where built_image.id = image_definition.last_built_image_id
)
where last_built_image_id is not null;

-- Drop foreign key constraint on image_id
alter table deployment
    drop constraint if exists fk_deployment_built_image;

-- Remove last_built_image_id and last_built_image_status from image_definition
alter table image_definition
    drop column if exists last_built_image_id;
alter table image_definition
    drop column if exists last_built_image_status;

-- Remove image_id from deployment
alter table deployment
    drop column if exists image_id;

