-- Add new columns to deployment table
ALTER TABLE deployment ADD image_definition_name NVARCHAR(255);
ALTER TABLE deployment ADD image_definition_version NVARCHAR(255);
go

-- Update new columns with data from image_definition table
UPDATE d
SET
    d.image_definition_name = i.name,
    d.image_definition_version = i.version
FROM deployment d
INNER JOIN image_definition i ON i.id = d.image_definition_id;