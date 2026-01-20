-- Drop foreign keys
ALTER TABLE deployment DROP CONSTRAINT fk_deployment_to_image_definition;
ALTER TABLE image_definition_topics DROP CONSTRAINT fk_image_definition_topics_to_image_definition;

ALTER TABLE mcp_image_definition DROP CONSTRAINT fk_mcp_image_definition_to_image_definition;
ALTER TABLE adapter_image_definition DROP CONSTRAINT fk_adapter_image_definition_to_image_definition;
ALTER TABLE interceptor_image_definition DROP CONSTRAINT fk_interceptor_image_definition_to_image_definition;

ALTER TABLE image_definition DROP CONSTRAINT df_image_definition_id;

ALTER TABLE image_definition DROP CONSTRAINT pk_image_definition;
ALTER TABLE mcp_image_definition DROP CONSTRAINT pk_mcp_image_definition;
ALTER TABLE adapter_image_definition DROP CONSTRAINT pk_adapter_image_definition;
ALTER TABLE interceptor_image_definition DROP CONSTRAINT pk_interceptor_image_definition;
ALTER TABLE image_definition_topics DROP CONSTRAINT pk_image_definition_topics;

-- Drop indexes
DROP INDEX idx_image_definition_name ON image_definition;
DROP INDEX idx_deployment_image_definition_id ON deployment;

-- Drop unique constraint for image_definition (type,name,version)
ALTER TABLE image_definition DROP CONSTRAINT uq_image_definition_type_name_version;
go

-- Change image_definition.id column type to VARCHAR(36)
ALTER TABLE image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Rename image_definition.name column to display_name and change type to NVARCHAR(512)
-- Using size '512' instead of 'MAX' because it restricts creation of constraints and indexes
ALTER TABLE image_definition ALTER COLUMN name NVARCHAR(512) NOT NULL;
EXEC sp_rename 'image_definition.name', 'display_name', 'COLUMN';
go

-- Change id column type in image_definition child tables to VARCHAR(36)
ALTER TABLE mcp_image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE adapter_image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE interceptor_image_definition ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Change deployment.image_definition_id column type to VARCHAR(36)
ALTER TABLE deployment ALTER COLUMN image_definition_id VARCHAR(36) NOT NULL;

-- Rename deployment.image_definition_name column to image_definition_display_name and change type to NVARCHAR(MAX)
ALTER TABLE deployment ALTER COLUMN image_definition_name NVARCHAR(MAX) NOT NULL;
EXEC sp_rename 'deployment.image_definition_name', 'image_definition_display_name', 'COLUMN';
go

-- Change image_definition_topics.image_definition_id column type to VARCHAR(36)
ALTER TABLE image_definition_topics ALTER COLUMN image_definition_id VARCHAR(36) NOT NULL;

-- Re-create indexes
CREATE INDEX idx_deployment_image_definition_id ON deployment(image_definition_id);
CREATE INDEX idx_image_definition_display_name ON image_definition(display_name);

-- Re-create unique constraint for image_definition (type,display_name,version)
ALTER TABLE image_definition ADD CONSTRAINT uq_image_definition_type_display_name_version UNIQUE (type, display_name, version);
go

-- Re-create foreign keys
ALTER TABLE image_definition ADD CONSTRAINT pk_image_definition PRIMARY KEY (id);
ALTER TABLE mcp_image_definition ADD CONSTRAINT pk_mcp_image_definition PRIMARY KEY (id);
ALTER TABLE adapter_image_definition ADD CONSTRAINT pk_adapter_image_definition PRIMARY KEY (id);
ALTER TABLE interceptor_image_definition ADD CONSTRAINT pk_interceptor_image_definition PRIMARY KEY (id);
ALTER TABLE image_definition_topics ADD CONSTRAINT pk_image_definition_topics PRIMARY KEY (image_definition_id, topic_name)


ALTER TABLE deployment ADD CONSTRAINT fk_deployment_to_image_definition FOREIGN KEY (image_definition_id) REFERENCES image_definition (id) ON DELETE NO ACTION;
ALTER TABLE image_definition_topics ADD CONSTRAINT fk_image_definition_topics_to_image_definition FOREIGN KEY (image_definition_id) REFERENCES image_definition (id) ON DELETE CASCADE;

ALTER TABLE mcp_image_definition ADD CONSTRAINT fk_mcp_image_definition_to_image_definition FOREIGN KEY (id) REFERENCES image_definition (id) ON DELETE NO ACTION;
ALTER TABLE adapter_image_definition ADD CONSTRAINT fk_adapter_image_definition_to_image_definition FOREIGN KEY (id) REFERENCES image_definition (id) ON DELETE NO ACTION;
ALTER TABLE interceptor_image_definition ADD CONSTRAINT fk_interceptor_image_definition_to_image_definition FOREIGN KEY (id) REFERENCES image_definition (id) ON DELETE NO ACTION;