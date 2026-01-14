alter table mcp_image_definition
    add mcp_endpoint_path nvarchar(max);
alter table mcp_image
    add mcp_endpoint_path nvarchar(max);
