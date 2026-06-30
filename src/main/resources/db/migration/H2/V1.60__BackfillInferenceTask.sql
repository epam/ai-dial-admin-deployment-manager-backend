-- Backfill existing inference deployments created before the inference_task column existed.
-- Sets them to NONE (the conservative default the API already reports for null); the real task is
-- re-detected on the deployment's next deploy/update. Audit (_aud) rows are left as-is to preserve
-- historical revision state.
UPDATE inference_deployment SET inference_task = 'NONE' WHERE inference_task IS NULL;
