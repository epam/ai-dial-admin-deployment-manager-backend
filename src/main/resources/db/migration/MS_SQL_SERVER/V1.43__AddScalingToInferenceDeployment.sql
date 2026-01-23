-- Add scaling column to inference_deployment table
ALTER TABLE inference_deployment ADD scaling VARCHAR(MAX);
go

-- Add JSON check constraint
ALTER TABLE inference_deployment
    ADD CONSTRAINT chk_inference_deployment_scaling_is_json CHECK (scaling IS NULL OR isjson(scaling) > 0);
go
