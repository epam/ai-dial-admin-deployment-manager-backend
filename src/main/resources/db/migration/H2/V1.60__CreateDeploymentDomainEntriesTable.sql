CREATE TABLE deployment_domain_entries (
    id            BIGINT AUTO_INCREMENT NOT NULL,
    deployment_id VARCHAR(36)  NOT NULL,
    domain        VARCHAR(255) NOT NULL,
    verdict       VARCHAR(10)  NOT NULL,
    observed_at   BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_deployment_domain_entry
        UNIQUE (deployment_id, domain, verdict),
    CONSTRAINT fk_deployment_domain_entry_deployment
        FOREIGN KEY (deployment_id) REFERENCES deployment(id) ON DELETE CASCADE
);
