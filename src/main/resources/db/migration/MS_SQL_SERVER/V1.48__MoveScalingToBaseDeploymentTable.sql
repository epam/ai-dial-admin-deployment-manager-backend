-- Add scaling column to base deployment table
ALTER TABLE deployment ADD scaling VARCHAR(MAX);
go

ALTER TABLE deployment
    ADD CONSTRAINT chk_deployment_scaling_is_json CHECK (scaling IS NULL OR isjson(scaling) > 0);
go

-- Migrate scaling data from child tables to base deployment table
UPDATE d SET d.scaling = c.scaling FROM deployment d INNER JOIN inference_deployment c ON c.id = d.id WHERE c.scaling IS NOT NULL;
go

UPDATE d SET d.scaling = c.scaling FROM deployment d INNER JOIN mcp_deployment c ON c.id = d.id WHERE c.scaling IS NOT NULL;
go

UPDATE d SET d.scaling = c.scaling FROM deployment d INNER JOIN adapter_deployment c ON c.id = d.id WHERE c.scaling IS NOT NULL;
go

UPDATE d SET d.scaling = c.scaling FROM deployment d INNER JOIN interceptor_deployment c ON c.id = d.id WHERE c.scaling IS NOT NULL;
go

-- Drop constraints from child tables
ALTER TABLE inference_deployment DROP CONSTRAINT chk_inference_deployment_scaling_is_json;
go

ALTER TABLE mcp_deployment DROP CONSTRAINT chk_mcp_deployment_scaling_is_json;
go

ALTER TABLE adapter_deployment DROP CONSTRAINT chk_adapter_deployment_scaling_is_json;
go

ALTER TABLE interceptor_deployment DROP CONSTRAINT chk_interceptor_deployment_scaling_is_json;
go

-- Drop scaling columns from child tables
ALTER TABLE inference_deployment DROP COLUMN scaling;
go

ALTER TABLE mcp_deployment DROP COLUMN scaling;
go

ALTER TABLE adapter_deployment DROP COLUMN scaling;
go

ALTER TABLE interceptor_deployment DROP COLUMN scaling;
go
