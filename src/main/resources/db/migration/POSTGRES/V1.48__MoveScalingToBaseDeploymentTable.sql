-- Add scaling column to base deployment table
ALTER TABLE deployment ADD COLUMN scaling JSONB;

-- Migrate scaling data from child tables to base deployment table
UPDATE deployment d SET scaling = c.scaling FROM inference_deployment c WHERE c.id = d.id AND c.scaling IS NOT NULL;
UPDATE deployment d SET scaling = c.scaling FROM mcp_deployment c WHERE c.id = d.id AND c.scaling IS NOT NULL;
UPDATE deployment d SET scaling = c.scaling FROM adapter_deployment c WHERE c.id = d.id AND c.scaling IS NOT NULL;
UPDATE deployment d SET scaling = c.scaling FROM interceptor_deployment c WHERE c.id = d.id AND c.scaling IS NOT NULL;

-- Drop scaling columns from child tables
ALTER TABLE inference_deployment DROP COLUMN scaling;
ALTER TABLE mcp_deployment DROP COLUMN scaling;
ALTER TABLE adapter_deployment DROP COLUMN scaling;
ALTER TABLE interceptor_deployment DROP COLUMN scaling;
