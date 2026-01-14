-- Create child tables for deployment entity hierarchy
create table mcp_deployment
(
    id                 uniqueidentifier not null,
    transport          nvarchar(64),
    mcp_endpoint_path  nvarchar(max),

    constraint pk_mcp_deployment primary key (id),
    constraint fk_mcp_deployment_to_deployment foreign key (id) references deployment (id) on delete no action
);

create table adapter_deployment
(
    id uniqueidentifier not null,

    constraint pk_adapter_deployment primary key (id),
    constraint fk_adapter_deployment_to_deployment foreign key (id) references deployment (id) on delete no action
);

create table interceptor_deployment
(
    id uniqueidentifier not null,

    constraint pk_interceptor_deployment primary key (id),
    constraint fk_interceptor_deployment_to_deployment foreign key (id) references deployment (id) on delete no action
);

create table nim_deployment
(
    id uniqueidentifier not null,

    constraint pk_nim_deployment primary key (id),
    constraint fk_nim_deployment_to_deployment foreign key (id) references deployment (id) on delete no action
);

-- Add metadata column to deployment
alter table deployment add metadata varchar(max);
go

-- Migrate container_port from image_definition to deployment where null
update d
set container_port = i.container_port
from deployment d
join image_definition i on d.image_definition_id = i.id
where d.container_port is null;

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
alter table mcp_image_definition add transport_type nvarchar(32);
go

update mcp_image_definition
set transport_type = case
                        when transport = 'STDIO' then 'LOCAL'
                        when transport in ('SSE', 'HTTP_STREAMING') then 'REMOTE'
                        else null
                    end;
go

-- Drop old columns
alter table mcp_image_definition drop column transport;
alter table mcp_image_definition drop column mcp_endpoint_path;
alter table image_definition drop column container_port;

-- Drop foreign check constraint on metadata
if exists (select * from sys.check_constraints where name = 'chk_image_definition_metadata_is_json')
begin
    alter table image_definition
        drop constraint chk_image_definition_metadata_is_json;
end;