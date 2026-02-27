-- Add scaling column to Knative deployment tables (mcp, adapter, interceptor)
ALTER TABLE mcp_deployment ADD scaling VARCHAR(MAX);
go

ALTER TABLE mcp_deployment
    ADD CONSTRAINT chk_mcp_deployment_scaling_is_json CHECK (scaling IS NULL OR isjson(scaling) > 0);
go

ALTER TABLE adapter_deployment ADD scaling VARCHAR(MAX);
go

ALTER TABLE adapter_deployment
    ADD CONSTRAINT chk_adapter_deployment_scaling_is_json CHECK (scaling IS NULL OR isjson(scaling) > 0);
go

ALTER TABLE interceptor_deployment ADD scaling VARCHAR(MAX);
go

ALTER TABLE interceptor_deployment
    ADD CONSTRAINT chk_interceptor_deployment_scaling_is_json CHECK (scaling IS NULL OR isjson(scaling) > 0);
go
