-- Drop existing unique constraint on image_definition.name
alter table image_definition
    drop constraint if exists uq_image_definition_name;

-- Add type column to image_definition table
alter table image_definition
    add column type varchar(20);

-- Update type column based on the entity type
update image_definition id
set type = 'MCP'
from mcp_image_definition mid
where id.id = mid.id;

update image_definition id
set type = 'ADAPTER'
from adapter_image_definition aid
where id.id = aid.id;

update image_definition id
set type = 'INTERCEPTOR'
from interceptor_image_definition iid
where id.id = iid.id;

update image_definition id
set type = 'NIM'
from nim_image_definition nid
where id.id = nid.id;

-- Make type column not nullable
alter table image_definition
    alter column type set not null;

-- Add unique constraint on (type, name, version) for image_definition
alter table image_definition
    add constraint uq_image_definition_type_name_version unique (type, name, version);
