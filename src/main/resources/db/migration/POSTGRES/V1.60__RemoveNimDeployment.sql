-- NIM deployment support was removed in 0.22.0. Delete every row that belongs to a NIM deployment and drop the NIM tables.

-- Activity feed and audit trail
DELETE FROM audit_activity WHERE resource_type = 'NimDeployment';
DELETE FROM deployment_topics_aud WHERE EXISTS (SELECT 1 FROM nim_deployment_aud n WHERE n.id = deployment_topics_aud.deployment_id AND n.rev = deployment_topics_aud.rev);
DELETE FROM deployment_aud WHERE EXISTS (SELECT 1 FROM nim_deployment_aud n WHERE n.id = deployment_aud.id AND n.rev = deployment_aud.rev);
DROP TABLE nim_deployment_aud;

-- Live rows. nim_deployment.id -> deployment.id is ON DELETE RESTRICT and the FK name differs per vendor,
-- so stage the ids, drop the subtype table (its FK goes with it), then delete the base rows.
CREATE TABLE nim_removed_ids (id VARCHAR(36) NOT NULL PRIMARY KEY);
INSERT INTO nim_removed_ids (id) SELECT id FROM nim_deployment;
DROP TABLE nim_deployment;
DELETE FROM deployment_topics WHERE deployment_id IN (SELECT id FROM nim_removed_ids);
DELETE FROM component_removal WHERE type = 'DEPLOYMENT' AND id IN (SELECT id FROM nim_removed_ids);
DELETE FROM deployment WHERE id IN (SELECT id FROM nim_removed_ids);
DROP TABLE nim_removed_ids;

-- Cleanup queue: NIM_SERVICE is no longer a known K8sResourceKind and would fail deserialization in the cleaner.
DELETE FROM disposable_resource WHERE resource_reference::text LIKE '%NIM_SERVICE%';
