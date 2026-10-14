# Tasks: Remove NIM Deployment Support

**Input**: Design documents from `/specs/028-remove-nim-deployments/`
**Prerequisites**: plan.md, spec.md

**Tests**: Included as regular tasks (constitution Testing Conventions). Each phase leaves the build green; code removal precedes the table drop because `ddl-auto: validate` fails on mapped-but-missing tables.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 = API, US2 = upgrade, US3 = legacy import

---

## Phase 1: Foundational — 400 for unconvertible parameters

- [X] T001 [US1] Add `testGetAllDeployments_withUnknownType_returnsBadRequest` to `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/DeploymentControllerTest.java`; verify it fails with 500
- [X] T002 [US1] Add `MethodArgumentTypeMismatchException` → 400 handler (`Invalid value '%s' for parameter '%s'`) to `src/main/java/com/epam/aidial/deployment/manager/web/handler/DefaultExceptionHandler.java`
- [X] T003 [US1] Add "Unconvertible request parameter" scenario to `specs/api-conventions/spec.md`

## Phase 2: Remove the NIM backend (Kubernetes, manifests, manager, config)

- [X] T004 [US2] Add `shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet` to `functional/tests/DeploymentFunctionalTest.java` (case-sensitive camelCase match, excluding `Nimbus*`); verify it fails
- [X] T005 [US2] Delete `NimDeploymentManager`, `NimManifestGenerator`, `NimProbeConverter`, `NimMappers`, `kubernetes/nim/*`, `NimServiceEventHandler`, `NimServiceInformerRegistration`, `NimDeployProperties`, `kubernetes/crd/nimservice.crd.yaml` and their tests/fixtures
- [X] T006 [US2] Remove `K8sResourceKind.NIM_SERVICE` and its handling in `DisposableResourceManager` / `DisposableResourceCleaner`; remove NIM fields from `AppProperties`; remove NIM blocks from `application.yml` and `local_env/docker-compose.yml`; reword NIM Javadoc
- [X] T007 [US2] Update tests (`DisposableResourceCleanerTest`, `K8sLocalConfiguration`, `PoolPrimitivesConverterTest`, NIM tests in functional suites) and the ArchUnit frozen store line
- [X] T008 [US2] Update `specs/kubernetes-cleanup/spec.md` and `specs/kubernetes-manifests/spec.md`

## Phase 3: Remove the NIM type from API, domain, persistence, export/import

- [X] T009 [US1] Add `testCreateDeployment_withLegacyNimType_returnsBadRequest` and `testGetAllDeployments_withLegacyNimTypeFilter_returnsBadRequest` to `DeploymentControllerTest`
- [X] T010 [US3] Add `import_ignoresLegacyNimDeploymentsBlock` to `functional/tests/ConfigExportImportFunctionalTest.java`, keeping `src/test/resources/config/expected_export_config_for_import.json`'s `nimDeployments` block as the legacy fixture; verify RED
- [X] T011 [US1] Delete NIM domain/DTO/entity types, `NgcRegistrySource`/`PersistenceNgcRegistrySource`, `ValidStorageSize`, `StorageSizeValidator`, `KubernetesQuantityParser` and their tests
- [X] T012 [US1] Remove NIM branches from web DTOs/mappers/validator, domain registrations, `DeploymentRepository`, `AuditActivityMapper`, `DeploymentManagerProvider`, `DeploymentSourceValidator`, `WorkloadClassifier`, config exporter/importer/previewers; drop `max-storage-size` from `application.yml`
- [X] T013 [US2] Remove `app.nim.enabled` from Postgres/SQL Server/K8s suites; keep it in `H2FunctionalTests` as a stale-config fixture
- [X] T014 Update `specs/deployments`, `export-import`, `auditing`, `deployment-metrics`, `image-definitions` capability specs

## Phase 4: Migration `V1.60__RemoveNimDeployment`

- [X] T015 [US2] Create abstract `functional/tests/NimRemovalMigrationFunctionalTest.java` (fresh DB → 1.59 → seed NIM + non-NIM data incl. a reused id → latest → assert) and wire it as `@Nested` in `H2FunctionalTests`, `PostgresFunctionalTests`, `SqlServerFunctionalTests`; verify RED on H2
- [X] T016 [US2] Create `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/V1.60__RemoveNimDeployment.sql` (audit rows matched on `(id, rev)`)
- [X] T017 [US2] Regenerate `docs/db-schema.md`; set schema version V1.60 in `specs/database-and-migrations/spec.md` and `specs/README.md`

## Phase 5: Docs and audit

- [X] T018 Tombstone `specs/nim-deployments/spec.md`; update `specs/README.md` rows
- [X] T019 [P] Remove NIM from `README.md`, root/`service`/`kubernetes` `CLAUDE.md`, `.specify/memory/constitution.md`
- [X] T020 [P] Remove NIM from `docs/configuration.md`, `docs/deployment_guide_full_security_model.md`, the infrastructure `.drawio`
- [X] T021 [P] Add `docs/upgrade-plans/0.23.0.md` and the 0.23.0 **Removed** section in `docs/INFRA-CHANGELOG.md`
- [X] T022 Final case-insensitive NIM audit grep; `./gradlew clean build`

## Follow-ups (out of scope)

- [ ] T023 Re-export `docs/diagramms/infrastructure_component/infrastrucutre-component-diagramm.png` from the updated `.drawio` (no draw.io available in the implementation environment)
