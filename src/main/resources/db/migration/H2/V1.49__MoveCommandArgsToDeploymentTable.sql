-- Add command and args columns to base deployment table
ALTER TABLE deployment ADD COLUMN command JSON;
ALTER TABLE deployment ADD COLUMN args JSON;

-- Migrate existing inference deployment command/args data to base deployment table
UPDATE deployment d SET command = (SELECT command FROM inference_deployment c WHERE c.id = d.id), args = (SELECT args FROM inference_deployment c WHERE c.id = d.id) WHERE d.id IN (SELECT id FROM inference_deployment WHERE command IS NOT NULL OR args IS NOT NULL);

-- Drop command and args columns from inference_deployment
ALTER TABLE inference_deployment DROP COLUMN command;
ALTER TABLE inference_deployment DROP COLUMN args;
