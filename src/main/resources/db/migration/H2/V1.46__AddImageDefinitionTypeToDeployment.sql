-- Add image_definition_type column to deployment table
ALTER TABLE deployment ADD COLUMN image_definition_type VARCHAR(20);

-- Backfill from image_definition table
UPDATE deployment
SET image_definition_type = (
    SELECT i.type FROM image_definition i WHERE i.id = deployment.image_definition_id
);
