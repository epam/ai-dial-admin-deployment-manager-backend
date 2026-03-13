-- Add accessed_domains column to image_definition (JSON stored as NVARCHAR(MAX))
alter table image_definition
    add accessed_domains nvarchar(max) not null default '[]';
