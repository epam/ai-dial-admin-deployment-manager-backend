-- DEFAULT 'NONE' backfills existing rows atomically; NOT NULL keeps the column self-consistent.
ALTER TABLE inference_deployment ADD COLUMN inference_task VARCHAR(32) DEFAULT 'NONE' NOT NULL;
-- Audit table stays nullable (historical revisions legitimately have no value) at the Envers default length.
ALTER TABLE inference_deployment_aud ADD COLUMN inference_task VARCHAR(255);
