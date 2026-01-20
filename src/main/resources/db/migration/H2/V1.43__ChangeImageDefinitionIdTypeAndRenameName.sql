-- Drop index for image_definition.name
DROP INDEX idx_image_definition_name ON image_definition;

-- Drop unique constraint for image_definition (type,name,version)
ALTER TABLE image_definition DROP CONSTRAINT uq_image_definition_type_name_version;

-- Drop default constraint on image_definition.id
ALTER TABLE image_definition ALTER COLUMN id SET DEFAULT NULL;

-- Change image_definition.id column type to VARCHAR(36)
ALTER TABLE image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Rename image_definition.name column to display_name and change type to TEXT
ALTER TABLE image_definition ALTER COLUMN name TEXT NOT NULL;
ALTER TABLE image_definition RENAME COLUMN name TO display_name;

-- Change id column type in image_definition child tables to VARCHAR(36)
ALTER TABLE mcp_image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE adapter_image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE interceptor_image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Change deployment.image_definition_id column type to VARCHAR(36)
ALTER TABLE deployment ALTER COLUMN image_definition_id VARCHAR(36) NOT NULL;

-- Rename deployment.image_definition_name column to image_definition_display_name and change type to TEXT
ALTER TABLE deployment ALTER COLUMN image_definition_name TEXT NOT NULL;
ALTER TABLE deployment RENAME COLUMN image_definition_name TO image_definition_display_name;

-- Change image_definition_topics.image_definition_id column type to VARCHAR(36)
ALTER TABLE image_definition_topics ALTER COLUMN image_definition_id VARCHAR(36) NOT NULL;

-- Create index on image_definition.display_name
CREATE INDEX idx_image_definition_display_name ON image_definition(display_name);

-- Create unique constraint for image_definition (type,display_name,version)
ALTER TABLE image_definition ADD CONSTRAINT uq_image_definition_type_display_name_version UNIQUE (type, display_name, version);
