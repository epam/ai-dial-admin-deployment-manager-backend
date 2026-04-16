-- Envers revision metadata
CREATE TABLE revinfo (
    id         INTEGER AUTO_INCREMENT NOT NULL,
    timestamp  BIGINT NOT NULL,
    author     VARCHAR(255),
    email      VARCHAR(320),
    PRIMARY KEY (id)
);

-- Deployment base audit trail (JOINED inheritance base)
CREATE TABLE deployment_aud (
    id                    VARCHAR(36) NOT NULL,
    rev                   INTEGER NOT NULL,
    revtype               SMALLINT,
    image_definition_id   UUID,
    source                JSON,
    display_name          TEXT,
    description           TEXT,
    envs                  JSON,
    metadata              JSON,
    scaling               JSON,
    resources             JSON,
    probe_properties      JSON,
    status                VARCHAR(255),
    url                   VARCHAR(2048),
    container_port        INTEGER,
    created_at_ms         BIGINT,
    updated_at_ms         BIGINT,
    author                VARCHAR(255),
    allowed_domains       JSON,
    service_name          VARCHAR(255),
    command               JSON,
    args                  JSON,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Adapter deployment audit (no additional columns)
CREATE TABLE adapter_deployment_aud (
    id  VARCHAR(36) NOT NULL,
    rev INTEGER NOT NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_adapter_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Application deployment audit (no additional columns)
CREATE TABLE application_deployment_aud (
    id  VARCHAR(36) NOT NULL,
    rev INTEGER NOT NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_application_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Interceptor deployment audit (no additional columns)
CREATE TABLE interceptor_deployment_aud (
    id  VARCHAR(36) NOT NULL,
    rev INTEGER NOT NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_interceptor_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- MCP deployment audit
CREATE TABLE mcp_deployment_aud (
    id                VARCHAR(36) NOT NULL,
    rev               INTEGER NOT NULL,
    transport         VARCHAR(255),
    mcp_endpoint_path TEXT,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_mcp_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- NIM deployment audit
CREATE TABLE nim_deployment_aud (
    id                  VARCHAR(36) NOT NULL,
    rev                 INTEGER NOT NULL,
    container_grpc_port INTEGER,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_nim_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Inference deployment audit
CREATE TABLE inference_deployment_aud (
    id           VARCHAR(36) NOT NULL,
    rev          INTEGER NOT NULL,
    model_format VARCHAR(255),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_inference_deployment_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Image definition base audit trail (JOINED inheritance base)
CREATE TABLE image_definition_aud (
    id              UUID NOT NULL,
    rev             INTEGER NOT NULL,
    revtype         SMALLINT,
    name            VARCHAR(255),
    description     TEXT,
    version         VARCHAR(255),
    type            VARCHAR(255),
    source          JSON,
    license         VARCHAR(255),
    created_at_ms   BIGINT,
    updated_at_ms   BIGINT,
    image_name      VARCHAR(255),
    build_status    VARCHAR(255),
    build_logs      JSON,
    built_at_ms     BIGINT,
    author          VARCHAR(255),
    allowed_domains JSON,
    image_builder   VARCHAR(255),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Adapter image definition audit (no additional columns)
CREATE TABLE adapter_image_definition_aud (
    id  UUID NOT NULL,
    rev INTEGER NOT NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_adapter_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Application image definition audit (no additional columns)
CREATE TABLE application_image_definition_aud (
    id  UUID NOT NULL,
    rev INTEGER NOT NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_application_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Interceptor image definition audit (no additional columns)
CREATE TABLE interceptor_image_definition_aud (
    id  UUID NOT NULL,
    rev INTEGER NOT NULL,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_interceptor_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- MCP image definition audit
CREATE TABLE mcp_image_definition_aud (
    id             UUID NOT NULL,
    rev            INTEGER NOT NULL,
    transport_type VARCHAR(255),
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_mcp_image_definition_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Domain whitelist audit
CREATE TABLE domain_whitelist_aud (
    id              UUID NOT NULL,
    rev             INTEGER NOT NULL,
    revtype         SMALLINT,
    allowed_domains JSON,
    updated_at      TIMESTAMP,
    PRIMARY KEY (id, rev),
    CONSTRAINT fk_domain_whitelist_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Deployment topics audit (ElementCollection)
CREATE TABLE deployment_topics_aud (
    rev           INTEGER NOT NULL,
    revtype       SMALLINT,
    deployment_id VARCHAR(36) NOT NULL,
    topic_name    VARCHAR(255) NOT NULL,
    PRIMARY KEY (deployment_id, topic_name, rev),
    CONSTRAINT fk_deployment_topics_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Image definition topics audit (ElementCollection)
CREATE TABLE image_definition_topics_aud (
    rev                 INTEGER NOT NULL,
    revtype             SMALLINT,
    image_definition_id UUID NOT NULL,
    topic_name          VARCHAR(255) NOT NULL,
    PRIMARY KEY (image_definition_id, topic_name, rev),
    CONSTRAINT fk_image_definition_topics_aud_rev FOREIGN KEY (rev) REFERENCES revinfo(id)
);

-- Denormalized activity feed
CREATE TABLE audit_activity (
    activity_id      VARCHAR(36) NOT NULL,
    activity_type    VARCHAR(36) NOT NULL,
    resource_type    VARCHAR(36),
    resource_id      VARCHAR(255),
    epoch_timestamp_ms BIGINT,
    initiated_author VARCHAR(255),
    initiated_email  VARCHAR(320),
    revision         INTEGER,
    PRIMARY KEY (activity_id),
    CONSTRAINT fk_audit_activity_rev FOREIGN KEY (revision) REFERENCES revinfo(id)
);

CREATE INDEX idx_revinfo_timestamp_id ON revinfo(timestamp DESC, id DESC);
