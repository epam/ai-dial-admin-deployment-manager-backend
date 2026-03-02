alter table mcp_deployment
    add image_reference nvarchar(max);
go

alter table adapter_deployment
    add image_reference nvarchar(max);
go

alter table interceptor_deployment
    add image_reference nvarchar(max);
go
