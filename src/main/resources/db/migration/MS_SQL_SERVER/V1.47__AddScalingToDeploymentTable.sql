-- Add scaling column to base deployment table
ALTER TABLE deployment ADD scaling VARCHAR(MAX);
go

ALTER TABLE deployment
    ADD CONSTRAINT chk_deployment_scaling_is_json CHECK (scaling IS NULL OR isjson(scaling) > 0);
go

-- Migrate existing inference scaling data to base deployment table
UPDATE d SET d.scaling = c.scaling FROM deployment d INNER JOIN inference_deployment c ON c.id = d.id WHERE c.scaling IS NOT NULL;
go

-- Drop constraint and column from inference_deployment (added in V1.43)
ALTER TABLE inference_deployment DROP CONSTRAINT chk_inference_deployment_scaling_is_json;
go

ALTER TABLE inference_deployment DROP COLUMN scaling;
go
