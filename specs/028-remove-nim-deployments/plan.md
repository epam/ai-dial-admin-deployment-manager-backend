# Implementation Plan: Remove NIM Deployment Support

**Branch**: `remove-nim-deployments` | **Date**: 2026-10-13 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/028-remove-nim-deployments/spec.md`

## Summary

Pure removal of the NIM deployment subtype in one release (0.23.0): delete the NIM API types, domain/persistence model, Kubernetes integration (NIMService CRD + crd2java-generated classes, client, informer, manifest generator, manager) and configuration; add one Flyway migration per vendor that deletes NIM data and drops the NIM tables; retire the `nim-deployments` capability spec to a tombstone and document the upgrade. A small, general fix (`MethodArgumentTypeMismatchException` → 400) is included so stale `type=NIM` filters return 400 rather than 500.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot, Jackson 3 (`tools.jackson`), MapStruct, Hibernate + Envers, Flyway (SQL + Java migrations), fabric8 Kubernetes client + `crd2java`
**Storage**: H2 / PostgreSQL / SQL Server via Flyway; new migration `V1.60__RemoveNimDeployment` in all three vendor directories
**Testing**: JUnit 5, MockMvc controller tests, functional suites (`H2FunctionalTests`, `PostgresFunctionalTests`, `SqlServerFunctionalTests` via Testcontainers), ArchUnit frozen store
**Target Platform**: Linux server (Kubernetes), unchanged
**Project Type**: Single backend service
**Performance Goals**: N/A
**Constraints**: Applied migrations V1.1–V1.59 immutable (Java migrations V1_34/V1_37/V1_50 still read `nim_deployment` on fresh DBs); `ddl-auto: validate` tolerates extra tables but not missing mapped ones, so code removal precedes the table drop
**Scale/Scope**: ~280 files matched "nim"; many false positives (`Nimbus*`, `applicatio**nIm**age…`, `minimum`)

## Constitution Check

- **I. Strict Layered Architecture**: removal only; the new handler lives in `web/handler`. **PASS**.
- **III. Kubernetes Isolation**: the whole `kubernetes/nim` package and its CRD are deleted; no new k8s calls. **PASS**.
- **Multi-Vendor Database Pattern**: V1.60 is added to H2, POSTGRES and MS_SQL_SERVER with vendor-specific JSON text matching; vendor parity kept; `docs/db-schema.md` regenerated, not hand-edited. **PASS**.
- **Testing Conventions**: regression tests for every changed contract; cross-vendor migration test. **PASS**.
- **spec-kit Workflow Rules**: capability specs updated in the same change; `nim-deployments` reduced to a tombstone. **PASS**.

## Design

### Code removal

- **API / web**: `DeploymentTypeDto.NIM`; `"nim"` subtypes in `CreateDeploymentRequestDto`, `DeploymentDto`, `DeploymentInternalDto`; the `Nim*Dto` classes; NIM branches in `DeploymentDtoMapper`, `ImportConfigDtoMapper`, `ExportConfigMapper`, `ImportConfigValidator`; `nimDeployments` in `ImportConfigPreviewDto`; `NIM_DEPLOYMENT` in `ExportConfigComponentTypeDto`. The storage-size chain (`ValidStorageSize`, `StorageSizeValidator`, `KubernetesQuantityParser`, `app.validation.resources.max-storage-size`) had no non-NIM callers and is deleted.
- **Domain / service**: `NimDeployment`, `CreateNimDeployment`, `NgcRegistrySource`, `NimDeploymentManager`, `NimMappers`; NIM cases in `DeploymentManagerProvider`, `DeploymentSourceValidator`, `WorkloadClassifier` (sealed switch); `ExportConfigComponentType.NIM_DEPLOYMENT`, `ActivityResourceType.NimDeployment`, NIM fields in `ExportConfig` / `ImportConfigPreview` and the exporter/importer/previewers.
- **Persistence**: `NimDeploymentEntity`, `PersistenceNgcRegistrySource`; NIM branches in `PersistenceDeploymentMapper`, `DeploymentRepository`, `AuditActivityMapper`.
- **Kubernetes**: `kubernetes/nim/*`, `NimServiceInformerRegistration`, `NimServiceEventHandler`, `NimManifestGenerator`, `NimProbeConverter`, `nimservice.crd.yaml`; `K8sResourceKind.NIM_SERVICE` and its handling in `DisposableResourceManager` / `DisposableResourceCleaner`.
- **Configuration**: `NimDeployProperties`; `AppProperties` NIM fields (keep `GPU_LIMIT_KEY`); `app.nim.*`, `nim-service-config`, `nim-service-expose-ingress-config` in `application.yml`; `K8S_NIM_ENABLED` in `local_env/docker-compose.yml`.

### Runtime behaviour

- Unknown `$type: "nim"` / `ngc_registry` → `HttpMessageNotReadableException` → 400.
- `?type=NIM` → new `MethodArgumentTypeMismatchException` handler → 400.
- Legacy `nimDeployments` in import archives → ignored (global `FAIL_ON_UNKNOWN_PROPERTIES` disabled); no shim.

### Migration `V1.60__RemoveNimDeployment`

Vendor-neutral SQL, FK-safe order:

1. `DELETE FROM audit_activity WHERE resource_type = 'NimDeployment'`.
2. Delete `deployment_topics_aud` and `deployment_aud` rows matching a `nim_deployment_aud` row on **(id, rev)** (correlated `EXISTS`) — ids are user-chosen and reusable, so an id-only match would wipe a non-NIM deployment's history. Drop `nim_deployment_aud`. Keep `revinfo`.
3. `nim_deployment.id → deployment.id` is `ON DELETE RESTRICT` with vendor-specific FK names, so: stage ids in `nim_removed_ids`, `DROP TABLE nim_deployment`, delete from `deployment_topics`, `component_removal (type='DEPLOYMENT')`, `deployment`, drop the staging table. No other table references `deployment.id`.
4. Delete `disposable_resource` rows with kind `NIM_SERVICE` (PostgreSQL `resource_reference::text LIKE '%NIM_SERVICE%'`, H2 `CAST(... AS VARCHAR)`, SQL Server plain `LIKE` on `VARCHAR(MAX)`), keeping the group's other resources for the cleaner.

### Docs

Tombstone `specs/nim-deployments/spec.md`; update affected capability specs and `specs/README.md`; remove NIM from `README.md`, `CLAUDE.md` files, constitution, `docs/configuration.md`, `docs/deployment_guide_full_security_model.md`, the `.drawio` diagram; add `docs/upgrade-plans/0.23.0.md` and a 0.23.0 **Removed** section in `docs/INFRA-CHANGELOG.md`.

## Project Structure

### Documentation (this feature)

```text
specs/028-remove-nim-deployments/
├── spec.md
├── plan.md
└── tasks.md
```

### Source Code

No new modules. Deletions and edits across `web/`, `service/`, `dao/`, `model/`, `kubernetes/`, `configuration/`, `cleanup/`; new SQL under `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/V1.60__RemoveNimDeployment.sql`; new test `src/test/java/com/epam/aidial/deployment/manager/functional/tests/NimRemovalMigrationFunctionalTest.java`.

## Complexity Tracking

| Risk | Mitigation |
|---|---|
| Irreversible deletion of NIM data and history | Accepted (Clarifications); cross-vendor test asserts exactly what is kept |
| Frontend contract break | Called out in upgrade plan; frontend ships matching change |
| Orphaned `NIMService` CRs / rolling-upgrade errors | Upgrade plan: verify CRs gone before upgrading; single replica / Recreate |
