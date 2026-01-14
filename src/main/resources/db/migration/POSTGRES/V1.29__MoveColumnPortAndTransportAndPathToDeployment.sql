-- Create child tables for deployment entity hierarchy
create table mcp_deployment
(
    id                 uuid not null,
    transport          varchar(64),
    mcp_endpoint_path  text,

    primary key (id),
    foreign key (id) references deployment (id) on delete restrict
);

create table adapter_deployment
(
    id uuid not null,

    primary key (id),
    foreign key (id) references deployment (id) on delete restrict
);

create table interceptor_deployment
(
    id uuid not null,

    primary key (id),
    foreign key (id) references deployment (id) on delete restrict
);

create table nim_deployment
(
    id uuid not null,

    primary key (id),
    foreign key (id) references deployment (id) on delete restrict
);

-- Add metadata column to deployment
alter table deployment add column metadata jsonb;

-- Migrate container_port from image_definition to deployment where null or empty
update deployment d
set container_port = i.container_port
from image_definition i
where d.image_definition_id = i.id and d.container_port is null;

-- Populate mcp_deployment table based on image definition type
insert into mcp_deployment (id, transport, mcp_endpoint_path)
select d.id, m.transport, m.mcp_endpoint_path
from deployment d
join mcp_image_definition m on d.image_definition_id = m.id;

-- Populate adapter_deployment table based on image definition type
insert into adapter_deployment (id)
select d.id
from deployment d
join adapter_image_definition a on d.image_definition_id = a.id;

-- Populate interceptor_deployment table based on image definition type
insert into interceptor_deployment (id)
select d.id
from deployment d
join interceptor_image_definition i on d.image_definition_id = i.id;

-- Populate nim_deployment table based on image definition type
insert into nim_deployment (id)
select d.id
from deployment d
join nim_image_definition n on d.image_definition_id = n.id;

-- Rename transport column to transport_type in mcp_image_definition and migrate data
alter table mcp_image_definition add column transport_type varchar(32);

update mcp_image_definition
set transport_type = case
                        when transport = 'STDIO' then 'LOCAL'
                        when transport in ('SSE', 'HTTP_STREAMING') then 'REMOTE'
                        else null
                    end;

-- Drop old columns
alter table mcp_image_definition drop column transport;
alter table mcp_image_definition drop column mcp_endpoint_path;
alter table image_definition drop column container_port;