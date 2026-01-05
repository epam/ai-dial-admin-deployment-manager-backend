-- Clean the table (it was not used before)
DELETE FROM inference_deployment;
go

-- Remove storage_uri column
ALTER TABLE inference_deployment DROP COLUMN storage_uri;
go

-- Add new columns
ALTER TABLE inference_deployment ADD model_format NVARCHAR(32) NOT NULL;
go

ALTER TABLE inference_deployment ADD source VARCHAR(MAX) NOT NULL;
go

ALTER TABLE inference_deployment ADD command VARCHAR(MAX);
go

-- Add JSON check constraints
ALTER TABLE inference_deployment
    ADD CONSTRAINT chk_inference_deployment_source_is_json CHECK (source IS NOT NULL AND isjson(source) > 0);
go

ALTER TABLE inference_deployment
    ADD CONSTRAINT chk_inference_deployment_command_is_json CHECK (command IS NULL OR isjson(command) > 0);
go

