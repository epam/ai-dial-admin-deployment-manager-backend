CREATE TABLE deployment_topics
(
    deployment_id VARCHAR(36)  NOT NULL,
    topic_name    VARCHAR(255) NOT NULL,

    CONSTRAINT pk_deployment_topics PRIMARY KEY (deployment_id, topic_name),
    CONSTRAINT fk_deployment_topics_deployment FOREIGN KEY (deployment_id) REFERENCES deployment (id) ON DELETE CASCADE
);
