CREATE TABLE inference_deployment
(
    id          UUID NOT NULL,
    storage_uri VARCHAR(2048),
    args        JSON,
    CONSTRAINT pk_inference_deployment PRIMARY KEY (id)
);

ALTER TABLE inference_deployment
    ADD CONSTRAINT FK_INFERENCE_DEPLOYMENT_ON_ID FOREIGN KEY (id) REFERENCES deployment (id) ON DELETE RESTRICT;
