-- Drop foreign keys
ALTER TABLE mcp_deployment DROP CONSTRAINT IF EXISTS mcp_deployment_id_fkey;
ALTER TABLE adapter_deployment DROP CONSTRAINT IF EXISTS adapter_deployment_id_fkey;
ALTER TABLE interceptor_deployment DROP CONSTRAINT IF EXISTS interceptor_deployment_id_fkey;
ALTER TABLE nim_deployment DROP CONSTRAINT IF EXISTS nim_deployment_id_fkey;
ALTER TABLE inference_deployment DROP CONSTRAINT IF EXISTS fk_inference_deployment_on_id;


-- Remove default from deployment.id
ALTER TABLE deployment ALTER COLUMN id DROP DEFAULT;

-- Change deployment.id type
ALTER TABLE deployment ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE deployment ALTER COLUMN id SET NOT NULL;

-- Rename deployment.name column to display_name and change type
ALTER TABLE deployment ALTER COLUMN name TYPE text;
ALTER TABLE deployment ALTER COLUMN name SET NOT NULL;
ALTER TABLE deployment RENAME COLUMN name TO display_name;

-- Change id column type in deployment child tables
ALTER TABLE mcp_deployment ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE mcp_deployment ALTER COLUMN id SET NOT NULL;

ALTER TABLE adapter_deployment ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE adapter_deployment ALTER COLUMN id SET NOT NULL;

ALTER TABLE interceptor_deployment ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE interceptor_deployment ALTER COLUMN id SET NOT NULL;

ALTER TABLE nim_deployment ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE nim_deployment ALTER COLUMN id SET NOT NULL;

ALTER TABLE inference_deployment ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE inference_deployment ALTER COLUMN id SET NOT NULL;

-- Change disposable_resource.group_id column type
ALTER TABLE disposable_resource ALTER COLUMN group_id TYPE varchar(36) USING group_id::varchar(36);
ALTER TABLE disposable_resource ALTER COLUMN group_id SET NOT NULL;

-- Change component_removal.id column type
ALTER TABLE component_removal ALTER COLUMN id TYPE varchar(36) USING id::varchar(36);
ALTER TABLE component_removal ALTER COLUMN id SET NOT NULL;


-- Re-create foreign keys
ALTER TABLE mcp_deployment
    ADD CONSTRAINT mcp_deployment_id_fkey FOREIGN KEY (id) REFERENCES deployment(id) ON DELETE RESTRICT;

ALTER TABLE adapter_deployment
    ADD CONSTRAINT adapter_deployment_id_fkey FOREIGN KEY (id) REFERENCES deployment(id) ON DELETE RESTRICT;

ALTER TABLE interceptor_deployment
    ADD CONSTRAINT interceptor_deployment_id_fkey FOREIGN KEY (id) REFERENCES deployment(id) ON DELETE RESTRICT;

ALTER TABLE nim_deployment
    ADD CONSTRAINT nim_deployment_id_fkey FOREIGN KEY (id) REFERENCES deployment(id) ON DELETE RESTRICT;

ALTER TABLE inference_deployment
    ADD CONSTRAINT inference_deployment_id_fkey FOREIGN KEY (id) REFERENCES deployment(id) ON DELETE RESTRICT;