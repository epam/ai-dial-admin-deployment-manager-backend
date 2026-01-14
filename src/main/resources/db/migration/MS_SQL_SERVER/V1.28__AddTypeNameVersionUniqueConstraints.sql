-- Drop existing unique constraint on image_definition.name
if exists (select * from sys.indexes where name = 'uq_image_definition_name' and object_id = object_id('image_definition'))
begin
    alter table image_definition
        drop constraint uq_image_definition_name;
end
go

-- Add type column to image_definition table
alter table image_definition
    add type varchar(20);
go

-- Update type column based on the entity type
update id
set id.type = 'MCP'
from image_definition id
join mcp_image_definition mid on id.id = mid.id;
go

update id
set id.type = 'ADAPTER'
from image_definition id
join adapter_image_definition aid on id.id = aid.id;
go

update id
set id.type = 'INTERCEPTOR'
from image_definition id
join interceptor_image_definition iid on id.id = iid.id;
go

update id
set id.type = 'NIM'
from image_definition id
join nim_image_definition nid on id.id = nid.id;
go

-- Make type column not nullable
alter table image_definition
    alter column type varchar(20) not null;
go

-- Add unique constraint on (type, name, version) for image_definition
alter table image_definition
    add constraint uq_image_definition_type_name_version unique (type, name, version);
go
