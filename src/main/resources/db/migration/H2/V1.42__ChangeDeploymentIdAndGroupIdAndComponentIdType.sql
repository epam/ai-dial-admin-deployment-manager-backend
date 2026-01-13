-- Drop default constraint on deployment.id
ALTER TABLE deployment ALTER COLUMN id SET DEFAULT NULL;

-- Change deployment.id column type to VARCHAR(36)
ALTER TABLE deployment ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Rename deployment.name column to display_name and change type to TEXT
ALTER TABLE deployment ALTER COLUMN name TEXT NOT NULL;
ALTER TABLE deployment RENAME COLUMN name TO display_name;

-- Change id column type in deployment child tables to VARCHAR(36)
ALTER TABLE mcp_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE adapter_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE interceptor_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE nim_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE inference_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Change disposable_resource.group_id column type to VARCHAR(36)
ALTER TABLE disposable_resource ALTER COLUMN group_id VARCHAR(36) NOT NULL;

-- Change component_removal.id column type to VARCHAR(36)
ALTER TABLE component_removal ALTER COLUMN id VARCHAR(36) NOT NULL;