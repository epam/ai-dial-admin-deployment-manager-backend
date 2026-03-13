-- Add accessed_domains column to image_definition (JSONB array of {domain, verdict})
alter table image_definition
    add column if not exists accessed_domains jsonb not null default '[]'::jsonb;
