-- DEFAULT 'NONE' backfills existing rows atomically; NOT NULL keeps the column self-consistent.
ALTER TABLE inference_deployment ADD inference_task VARCHAR(32) NOT NULL DEFAULT 'NONE';
go
-- Audit table stays nullable (historical revisions legitimately have no value) at the Envers default length.
ALTER TABLE inference_deployment_aud ADD inference_task VARCHAR(255);
go
