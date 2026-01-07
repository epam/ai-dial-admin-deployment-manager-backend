-- Update disposable_resource: set lifecycle_state to TO_CLEANUP for all NIM image definitions
update disposable_resource
set lifecycle_state = 'TO_CLEANUP'
where group_id in (select id from nim_image_definition);

-- Delete from nim_image_definition (removes foreign key constraint)
delete from nim_image_definition;

-- Delete all NIM rows from image_definition table
delete from image_definition
where type = 'NIM';

-- Drop nim_image_definition table
drop table nim_image_definition;

