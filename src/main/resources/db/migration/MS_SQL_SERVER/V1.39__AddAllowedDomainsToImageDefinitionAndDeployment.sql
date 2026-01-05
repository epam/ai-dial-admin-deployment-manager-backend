-- Create 'domain_whitelist' table
create table domain_whitelist
(
    id                  uniqueidentifier  not null,
    allowed_domains     varchar(max)      not null,
    updated_at          datetimeoffset(6) not null,

    constraint pk_domain_whitelist primary key (id)
);
go

-- Insert first & only record
insert into domain_whitelist (id, allowed_domains, updated_at) values (NEWID(), '[]', SYSDATETIMEOFFSET());

-- Add 'allowed_domains' column to 'image_definition' table
alter table image_definition
    add allowed_domains varchar(max) not null default '[]';

-- Add 'allowed_domains' column to 'deployment' table
alter table deployment
    add allowed_domains varchar(max) not null default '[]';
