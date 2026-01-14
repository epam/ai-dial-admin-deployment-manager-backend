-- Add new columns to deployment table
ALTER TABLE deployment ADD COLUMN image_definition_name VARCHAR(255);
ALTER TABLE deployment ADD COLUMN image_definition_version VARCHAR(255);

-- Update new columns with data from image_definition table
UPDATE deployment
SET image_definition_name = (
    SELECT i.name FROM image_definition i WHERE i.id = deployment.image_definition_id
),
    image_definition_version = (
    SELECT i.version FROM image_definition i WHERE i.id = deployment.image_definition_id
);