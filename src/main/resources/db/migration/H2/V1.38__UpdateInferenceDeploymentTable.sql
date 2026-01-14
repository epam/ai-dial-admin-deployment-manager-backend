-- Clean the table (it was not used before)
DELETE FROM inference_deployment;

-- Remove storage_uri column
ALTER TABLE inference_deployment DROP COLUMN storage_uri;

-- Add new columns
ALTER TABLE inference_deployment ADD COLUMN model_format VARCHAR(32) NOT NULL;
ALTER TABLE inference_deployment ADD COLUMN source JSON NOT NULL;
ALTER TABLE inference_deployment ADD COLUMN command JSON;

