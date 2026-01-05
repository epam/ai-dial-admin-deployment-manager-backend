-- Create 'domain_whitelist' table
create table domain_whitelist
(
    id                uuid          not null,
    allowed_domains   json          not null,
    updated_at        timestamp(3)  not null,

    constraint pk_domain_whitelist primary key (id)
);

-- Insert first & only record
insert into domain_whitelist (id, allowed_domains, updated_at) values (gen_random_uuid(), '[]', CURRENT_TIMESTAMP(3));

-- Add 'allowed_domains' column to 'image_definition' table
alter table image_definition
    add column if not exists allowed_domains json not null default '[]';

-- Add 'allowed_domains' column to 'deployment' table
alter table deployment
    add column if not exists allowed_domains json not null default '[]';
