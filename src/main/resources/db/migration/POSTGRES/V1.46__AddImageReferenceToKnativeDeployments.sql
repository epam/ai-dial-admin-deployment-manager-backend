alter table mcp_deployment
    add column image_reference text;

alter table adapter_deployment
    add column image_reference text;

alter table interceptor_deployment
    add column image_reference text;
