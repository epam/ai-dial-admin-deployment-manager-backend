-- Add command and args columns to base deployment table
ALTER TABLE deployment ADD COLUMN command JSONB;
ALTER TABLE deployment ADD COLUMN args JSONB;

-- Migrate existing inference deployment command/args data to base deployment table
UPDATE deployment d SET command = c.command, args = c.args FROM inference_deployment c WHERE c.id = d.id AND (c.command IS NOT NULL OR c.args IS NOT NULL);

-- Drop command and args columns from inference_deployment
ALTER TABLE inference_deployment DROP COLUMN command;
ALTER TABLE inference_deployment DROP COLUMN args;
