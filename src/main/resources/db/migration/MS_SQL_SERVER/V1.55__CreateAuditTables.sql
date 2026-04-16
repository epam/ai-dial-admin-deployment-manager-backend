-- Envers revision metadata
CREATE TABLE revinfo (
    id        INTEGER IDENTITY(1,1) NOT NULL,
    timestamp BIGINT NOT NULL,
    author    VARCHAR(255),
    email     VARCHAR(320),
    CONSTRAINT pk_revinfo PRIMARY KEY (id)
);
go

-- Deployment base audit trail (JOINED inheritance base)
CREATE TABLE deployment_aud (
    id                  VARCHAR(36) NOT NULL,
    rev                 INTEGER NOT NULL,
    revtype             SMALLINT,
    image_definition_id UNIQUEIDENTIFIER,
    source              VARCHAR(MAX),
    display_name        NVARCHAR(MAX),
    description         NVARCHAR(MAX),
    envs                VARCHAR(MAX),
    metadata            VARCHAR(MAX),
    scaling             VARCHAR(MAX),
    resources           VARCHAR(MAX),
    probe_properties    VARCHAR(MAX),
    status              VARCHAR(255),
    url                 VARCHAR(2048),
    container_port      INTEGER,
    created_at_ms       BIGINT,
    updated_at_ms       BIGINT,
    author              VARCHAR(255),
    allowed_domains     VARCHAR(MAX),
    service_name        VARCHAR(255),
    command             VARCHAR(MAX),
    args                VARCHAR(MAX),
    CONSTRAINT pk_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Adapter deployment audit (no additional columns)
CREATE TABLE adapter_deployment_aud (
    id  VARCHAR(36) NOT NULL,
    rev INTEGER NOT NULL,
    CONSTRAINT pk_adapter_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_adapter_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Application deployment audit (no additional columns)
CREATE TABLE application_deployment_aud (
    id  VARCHAR(36) NOT NULL,
    rev INTEGER NOT NULL,
    CONSTRAINT pk_application_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_application_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Interceptor deployment audit (no additional columns)
CREATE TABLE interceptor_deployment_aud (
    id  VARCHAR(36) NOT NULL,
    rev INTEGER NOT NULL,
    CONSTRAINT pk_interceptor_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_interceptor_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- MCP deployment audit
CREATE TABLE mcp_deployment_aud (
    id                VARCHAR(36) NOT NULL,
    rev               INTEGER NOT NULL,
    transport         VARCHAR(255),
    mcp_endpoint_path NVARCHAR(MAX),
    CONSTRAINT pk_mcp_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_mcp_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- NIM deployment audit
CREATE TABLE nim_deployment_aud (
    id                  VARCHAR(36) NOT NULL,
    rev                 INTEGER NOT NULL,
    container_grpc_port INTEGER,
    CONSTRAINT pk_nim_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_nim_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Inference deployment audit
CREATE TABLE inference_deployment_aud (
    id           VARCHAR(36) NOT NULL,
    rev          INTEGER NOT NULL,
    model_format VARCHAR(255),
    CONSTRAINT pk_inference_deployment_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_inference_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Image definition base audit trail (JOINED inheritance base)
CREATE TABLE image_definition_aud (
    id              UNIQUEIDENTIFIER NOT NULL,
    rev             INTEGER NOT NULL,
    revtype         SMALLINT,
    name            VARCHAR(255),
    description     NVARCHAR(MAX),
    version         VARCHAR(255),
    type            VARCHAR(255),
    source          VARCHAR(MAX),
    license         VARCHAR(255),
    created_at_ms   BIGINT,
    updated_at_ms   BIGINT,
    image_name      VARCHAR(255),
    build_status    VARCHAR(255),
    build_logs      VARCHAR(MAX),
    built_at_ms     BIGINT,
    author          VARCHAR(255),
    allowed_domains VARCHAR(MAX),
    image_builder   VARCHAR(255),
    CONSTRAINT pk_image_definition_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Adapter image definition audit (no additional columns)
CREATE TABLE adapter_image_definition_aud (
    id  UNIQUEIDENTIFIER NOT NULL,
    rev INTEGER NOT NULL,
    CONSTRAINT pk_adapter_image_definition_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_adapter_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Application image definition audit (no additional columns)
CREATE TABLE application_image_definition_aud (
    id  UNIQUEIDENTIFIER NOT NULL,
    rev INTEGER NOT NULL,
    CONSTRAINT pk_application_image_definition_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_application_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Interceptor image definition audit (no additional columns)
CREATE TABLE interceptor_image_definition_aud (
    id  UNIQUEIDENTIFIER NOT NULL,
    rev INTEGER NOT NULL,
    CONSTRAINT pk_interceptor_image_definition_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_interceptor_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- MCP image definition audit
CREATE TABLE mcp_image_definition_aud (
    id             UNIQUEIDENTIFIER NOT NULL,
    rev            INTEGER NOT NULL,
    transport_type VARCHAR(255),
    CONSTRAINT pk_mcp_image_definition_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_mcp_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Domain whitelist audit
CREATE TABLE domain_whitelist_aud (
    id              UNIQUEIDENTIFIER NOT NULL,
    rev             INTEGER NOT NULL,
    revtype         SMALLINT,
    allowed_domains VARCHAR(MAX),
    updated_at      DATETIMEOFFSET(6),
    CONSTRAINT pk_domain_whitelist_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_domain_whitelist_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Deployment topics audit (ElementCollection)
CREATE TABLE deployment_topics_aud (
    rev           INTEGER NOT NULL,
    revtype       SMALLINT,
    deployment_id VARCHAR(36) NOT NULL,
    topic_name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_deployment_topics_aud PRIMARY KEY (deployment_id, topic_name, rev),
    CONSTRAINT fk_deployment_topics_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Image definition topics audit (ElementCollection)
CREATE TABLE image_definition_topics_aud (
    rev                 INTEGER NOT NULL,
    revtype             SMALLINT,
    image_definition_id UNIQUEIDENTIFIER NOT NULL,
    topic_name          VARCHAR(255) NOT NULL,
    CONSTRAINT pk_image_definition_topics_aud PRIMARY KEY (image_definition_id, topic_name, rev),
    CONSTRAINT fk_image_definition_topics_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);
go

-- Denormalized activity feed
CREATE TABLE audit_activity (
    activity_id        VARCHAR(36) NOT NULL,
    activity_type      VARCHAR(36) NOT NULL,
    resource_type      VARCHAR(36),
    resource_id        VARCHAR(255),
    epoch_timestamp_ms BIGINT,
    initiated_author   VARCHAR(255),
    initiated_email    VARCHAR(320),
    revision           INTEGER,
    CONSTRAINT pk_audit_activity PRIMARY KEY (activity_id),
    CONSTRAINT fk_audit_activity_rev FOREIGN KEY (revision) REFERENCES revinfo(id)
);
go

CREATE INDEX idx_revinfo_timestamp_id ON revinfo(timestamp DESC, id DESC);
