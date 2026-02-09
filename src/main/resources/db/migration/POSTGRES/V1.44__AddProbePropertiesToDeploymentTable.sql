-- Add probe_properties column to deployment table
ALTER TABLE deployment
    ADD COLUMN probe_properties JSONB;
