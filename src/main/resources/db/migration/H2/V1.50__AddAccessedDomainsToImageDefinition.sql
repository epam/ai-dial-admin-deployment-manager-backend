-- Add accessed_domains column to image_definition (JSON array of {domain, verdict})
alter table image_definition
    add column if not exists accessed_domains json default '[]';
