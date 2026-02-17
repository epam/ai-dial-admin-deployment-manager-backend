-- Add probe_properties column to deployment table
ALTER TABLE deployment ADD probe_properties VARCHAR(MAX);
go

ALTER TABLE deployment
    ADD CONSTRAINT chk_deployment_probe_properties_is_json CHECK (probe_properties IS NULL OR isjson(probe_properties) > 0);
go
