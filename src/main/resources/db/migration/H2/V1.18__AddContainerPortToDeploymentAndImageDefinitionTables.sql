alter table deployment
    add column container_port integer;
alter table image_definition
    add column container_port integer;
