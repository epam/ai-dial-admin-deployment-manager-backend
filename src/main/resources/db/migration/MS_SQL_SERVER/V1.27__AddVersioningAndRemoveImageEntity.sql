-- Add version column to image_definition
alter table image_definition
    add version nvarchar(255) not null default '1.0.0';
go

-- Add build-related columns to image_definition
alter table image_definition
    add image_name nvarchar(255);
go

alter table image_definition
    add build_status nvarchar(32);
go

alter table image_definition
    add build_logs varchar(max);
go

alter table image_definition
    add built_at_ms bigint;
go

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
go

-- Drop foreign key constraint on image_id
if exists (select * from sys.foreign_keys where name = 'fk_deployment_to_built_image')
begin
    alter table deployment
        drop constraint fk_deployment_to_built_image;
end
go

-- Remove last_built_image_id and last_built_image_status from image_definition
if exists (select * from sys.columns where object_id = object_id('image_definition') and name = 'last_built_image_id')
begin
    alter table image_definition
        drop column last_built_image_id;
end
go

if exists (select * from sys.columns where object_id = object_id('image_definition') and name = 'last_built_image_status')
begin
    alter table image_definition
        drop column last_built_image_status;
end
go

-- Remove image_id from deployment
if exists (select * from sys.columns where object_id = object_id('deployment') and name = 'image_id')
begin
    alter table deployment
        drop column image_id;
end
go

