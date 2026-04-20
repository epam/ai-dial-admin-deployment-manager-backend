CREATE TABLE image_build_logs (
    image_definition_id UUID NOT NULL,
    logs                JSON,
    PRIMARY KEY (image_definition_id),
    CONSTRAINT fk_image_build_logs_image_definition
        FOREIGN KEY (image_definition_id) REFERENCES image_definition(id) ON DELETE CASCADE
);

INSERT INTO image_build_logs (image_definition_id, logs)
SELECT id, build_logs FROM image_definition WHERE build_logs IS NOT NULL;

ALTER TABLE image_definition DROP COLUMN build_logs;
