alter table mcp_image_definition
    add column mcp_endpoint_path text;
alter table mcp_image
    add column mcp_endpoint_path text;
