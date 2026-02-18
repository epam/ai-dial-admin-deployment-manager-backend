-- Add image_definition_type column to deployment table
ALTER TABLE deployment ADD COLUMN image_definition_type VARCHAR(20);

-- Backfill from image_definition table
UPDATE deployment d
SET image_definition_type = i.type
FROM image_definition i
WHERE i.id = d.image_definition_id;
