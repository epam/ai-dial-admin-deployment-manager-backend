-- Add command and args columns to base deployment table
ALTER TABLE deployment ADD command VARCHAR(MAX);
ALTER TABLE deployment ADD args VARCHAR(MAX);
ALTER TABLE deployment
    ADD CONSTRAINT chk_deployment_command_is_json CHECK (command IS NULL OR isjson(command) > 0);
ALTER TABLE deployment
    ADD CONSTRAINT chk_deployment_args_is_json CHECK (args IS NULL OR isjson(args) > 0);
go

-- Migrate existing inference deployment command/args data to base deployment table
UPDATE d SET d.command = c.command, d.args = c.args FROM deployment d INNER JOIN inference_deployment c ON c.id = d.id WHERE c.command IS NOT NULL OR c.args IS NOT NULL;
go

-- Drop constraints and columns from inference_deployment
ALTER TABLE inference_deployment DROP CONSTRAINT chk_inference_deployment_command_is_json;
ALTER TABLE inference_deployment DROP CONSTRAINT chk_inference_deployment_args_is_json;
ALTER TABLE inference_deployment DROP COLUMN command;
ALTER TABLE inference_deployment DROP COLUMN args;
go
