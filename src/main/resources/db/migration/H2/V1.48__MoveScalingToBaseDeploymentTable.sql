-- Add scaling column to base deployment table
ALTER TABLE deployment ADD COLUMN scaling JSON;

-- Migrate scaling data from child tables to base deployment table
UPDATE deployment d SET scaling = (SELECT scaling FROM inference_deployment c WHERE c.id = d.id) WHERE d.id IN (SELECT id FROM inference_deployment WHERE scaling IS NOT NULL);
UPDATE deployment d SET scaling = (SELECT scaling FROM mcp_deployment c WHERE c.id = d.id) WHERE d.id IN (SELECT id FROM mcp_deployment WHERE scaling IS NOT NULL);
UPDATE deployment d SET scaling = (SELECT scaling FROM adapter_deployment c WHERE c.id = d.id) WHERE d.id IN (SELECT id FROM adapter_deployment WHERE scaling IS NOT NULL);
UPDATE deployment d SET scaling = (SELECT scaling FROM interceptor_deployment c WHERE c.id = d.id) WHERE d.id IN (SELECT id FROM interceptor_deployment WHERE scaling IS NOT NULL);

-- Drop scaling columns from child tables
ALTER TABLE inference_deployment DROP COLUMN scaling;
ALTER TABLE mcp_deployment DROP COLUMN scaling;
ALTER TABLE adapter_deployment DROP COLUMN scaling;
ALTER TABLE interceptor_deployment DROP COLUMN scaling;
