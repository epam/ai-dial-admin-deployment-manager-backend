-- Add image_definition_type column to deployment table
ALTER TABLE deployment ADD image_definition_type NVARCHAR(20);
go

-- Backfill from image_definition table
UPDATE d
SET d.image_definition_type = i.type
FROM deployment d
INNER JOIN image_definition i ON i.id = d.image_definition_id;
go
