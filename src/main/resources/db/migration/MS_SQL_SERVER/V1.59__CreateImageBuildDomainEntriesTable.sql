CREATE TABLE image_build_domain_entries (
    id                  BIGINT           IDENTITY(1,1) NOT NULL,
    image_definition_id UNIQUEIDENTIFIER NOT NULL,
    domain              VARCHAR(255)     NOT NULL,
    verdict             VARCHAR(10)      NOT NULL,
    observed_at         BIGINT           NOT NULL,
    CONSTRAINT pk_image_build_domain_entries PRIMARY KEY (id),
    CONSTRAINT uq_image_build_domain_entry
        UNIQUE (image_definition_id, domain, verdict),
    CONSTRAINT fk_image_build_domain_entry_image_def
        FOREIGN KEY (image_definition_id) REFERENCES image_definition(id) ON DELETE CASCADE
);
go
