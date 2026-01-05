-- Add author_name and author_email columns to deployment table
alter table deployment
    add column author_name text;
alter table deployment
    add column author_email text;

-- Add author_name and author_email columns to image_definition table
alter table image_definition
    add column author_name text;
alter table image_definition
    add column author_email text;

-- Add author_name and author_email columns to built_image table
alter table built_image
    add column author_name text;
alter table built_image
    add column author_email text;
