CREATE TABLE inference_deployment
(
    id          UNIQUEIDENTIFIER NOT NULL,
    storage_uri NVARCHAR(2048),
    args        VARCHAR(MAX),
    CONSTRAINT pk_inference_deployment PRIMARY KEY (id),
    CONSTRAINT chk_inference_deployment_args_is_json CHECK (args IS NULL OR isjson(args) > 0)
);
go

ALTER TABLE inference_deployment
    ADD CONSTRAINT FK_INFERENCE_DEPLOYMENT_ON_ID FOREIGN KEY (id) REFERENCES deployment (id) ON DELETE NO ACTION;
