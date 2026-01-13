-- Drop constraints and index
ALTER TABLE mcp_deployment DROP CONSTRAINT fk_mcp_deployment_to_deployment;
ALTER TABLE adapter_deployment DROP CONSTRAINT fk_adapter_deployment_to_deployment;
ALTER TABLE interceptor_deployment DROP CONSTRAINT fk_interceptor_deployment_to_deployment;
ALTER TABLE nim_deployment DROP CONSTRAINT fk_nim_deployment_to_deployment;
ALTER TABLE inference_deployment DROP CONSTRAINT FK_INFERENCE_DEPLOYMENT_ON_ID;
go

ALTER TABLE deployment DROP CONSTRAINT df_deployment_id;

ALTER TABLE deployment DROP CONSTRAINT pk_deployment;
ALTER TABLE mcp_deployment DROP CONSTRAINT pk_mcp_deployment;
ALTER TABLE adapter_deployment DROP CONSTRAINT pk_adapter_deployment;
ALTER TABLE interceptor_deployment DROP CONSTRAINT pk_interceptor_deployment;
ALTER TABLE nim_deployment DROP CONSTRAINT pk_nim_deployment;
ALTER TABLE inference_deployment DROP CONSTRAINT pk_inference_deployment;
ALTER TABLE component_removal DROP CONSTRAINT pk_component_removal;

DROP INDEX idx_disposable_resource_group_id ON disposable_resource;
go

-- Change deployment.id type to varchar(36)
ALTER TABLE deployment ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Rename deployment.name to display_name and change type to NVARCHAR(MAX)
ALTER TABLE deployment ALTER COLUMN name NVARCHAR(MAX) NOT NULL;
EXEC sp_rename 'deployment.name', 'display_name', 'COLUMN';
go

-- Change id column type in deployment child tables to varchar(36)
ALTER TABLE mcp_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE adapter_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE interceptor_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE nim_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;
ALTER TABLE inference_deployment ALTER COLUMN id VARCHAR(36) NOT NULL;

-- Change disposable_resource.group_id column type to varchar(36)
ALTER TABLE disposable_resource ALTER COLUMN group_id VARCHAR(36) NOT NULL;

-- Change component_removal.id column type to varchar(36)
ALTER TABLE component_removal ALTER COLUMN id VARCHAR(36) NOT NULL;
go

-- Re-create foreign keys and index
ALTER TABLE deployment ADD CONSTRAINT pk_deployment PRIMARY KEY (id);
ALTER TABLE mcp_deployment ADD CONSTRAINT pk_mcp_deployment PRIMARY KEY (id);
ALTER TABLE adapter_deployment ADD CONSTRAINT pk_adapter_deployment PRIMARY KEY (id);
ALTER TABLE interceptor_deployment ADD CONSTRAINT pk_interceptor_deployment PRIMARY KEY (id);
ALTER TABLE nim_deployment ADD CONSTRAINT pk_nim_deployment PRIMARY KEY (id);
ALTER TABLE inference_deployment ADD CONSTRAINT pk_inference_deployment PRIMARY KEY (id);
ALTER TABLE component_removal ADD CONSTRAINT pk_component_removal PRIMARY KEY (id);

ALTER TABLE mcp_deployment
    ADD CONSTRAINT fk_mcp_deployment_to_deployment FOREIGN KEY (id) REFERENCES deployment(id);

ALTER TABLE adapter_deployment
    ADD CONSTRAINT fk_adapter_deployment_to_deployment FOREIGN KEY (id) REFERENCES deployment(id);

ALTER TABLE interceptor_deployment
    ADD CONSTRAINT fk_interceptor_deployment_to_deployment FOREIGN KEY (id) REFERENCES deployment(id);

ALTER TABLE nim_deployment
    ADD CONSTRAINT fk_nim_deployment_to_deployment FOREIGN KEY (id) REFERENCES deployment(id);

ALTER TABLE inference_deployment
    ADD CONSTRAINT fk_inference_deployment_to_deployment FOREIGN KEY (id) REFERENCES deployment(id);

CREATE INDEX idx_disposable_resource_group_id ON disposable_resource(group_id);