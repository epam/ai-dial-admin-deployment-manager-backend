-- Add new columns to deployment table
ALTER TABLE deployment ADD COLUMN image_definition_name VARCHAR(255);
ALTER TABLE deployment ADD COLUMN image_definition_version VARCHAR(255);

-- Update new columns with data from image_definition table
UPDATE deployment d
SET
    image_definition_name = i.name,
    image_definition_version = i.version
FROM image_definition i
WHERE i.id = d.image_definition_id;