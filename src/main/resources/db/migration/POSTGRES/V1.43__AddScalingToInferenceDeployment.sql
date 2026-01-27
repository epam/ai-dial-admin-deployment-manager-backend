-- Add scaling column to inference_deployment table
ALTER TABLE inference_deployment ADD COLUMN scaling JSONB;
