-- Add scaling column to Knative deployment tables (mcp, adapter, interceptor)
ALTER TABLE mcp_deployment ADD COLUMN scaling JSONB;
ALTER TABLE adapter_deployment ADD COLUMN scaling JSONB;
ALTER TABLE interceptor_deployment ADD COLUMN scaling JSONB;
