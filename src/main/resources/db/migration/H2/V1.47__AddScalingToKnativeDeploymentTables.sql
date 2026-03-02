-- Add scaling column to Knative deployment tables (mcp, adapter, interceptor)
ALTER TABLE mcp_deployment ADD COLUMN scaling JSON;
ALTER TABLE adapter_deployment ADD COLUMN scaling JSON;
ALTER TABLE interceptor_deployment ADD COLUMN scaling JSON;
