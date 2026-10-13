# Remove NIM Deployment Support — Design

**Date**: 2026-10-13
**Target release**: 0.22.0
**Capabilities affected**: nim-deployments (retired), deployments, kubernetes-manifests, kubernetes-cleanup, export-import, auditing, deployment-metrics, image-definitions

## Goal

NVIDIA NIM models are no longer used. Remove every NIM-specific API contract, domain type, persistence table, Kubernetes integration, configuration property, test and live doc from the codebase in a single release.

**Success criteria**

- No NIM deployment type, NGC registry source, `NIMService` CRD handling or NIM configuration remains in `src/main` or `src/test`.
- Existing databases upgrade cleanly: NIM data is deleted and NIM tables are dropped on H2, PostgreSQL and SQL Server.
- `./gradlew test` (incl. testcontainers) and `checkstyleMain checkstyleTest` pass.
- A final case-insensitive `nim` grep yields only known keepers (see *Out of scope*).

## Decisions

| Decision | Choice |
|---|---|
| Rollout | Single release, full removal (no deprecation phase, no feature-flag-only removal) |
| Existing NIM data | Deleted by a new migration; NIM tables dropped |
| Live `NIMService` CRs | Not touched by the app; operators remove them before upgrading (documented) |
| Importing old export ZIPs containing NIM | Silently ignored — the `nimDeployments` block is dropped, everything else imports (no legacy shim). Originally chosen as "reject with 400"; revised after discovering `FAIL_ON_UNKNOWN_PROPERTIES` is globally disabled, so rejection would need an explicit guard |
| `specs/nim-deployments/spec.md` | Reduced to a tombstone (`Status: Removed`) to keep links from historical specs valid |

## Out of scope (kept intentionally)

- Already-applied Flyway migrations (V1.12, V1.28, V1.29, V1.34, V1.37, V1.40, V1.42, V1.55, V1.57, …) — editing breaks checksums.
- `Nimbus*` JWT classes and the `com.nimbusds:nimbus-jose-jwt` dependency — unrelated to NIM.
- Historical numbered specs `012-nim-url-schema`, `013-nim-served-model-name`, `015-nim-kserve-migration`, and NIM mentions inside other numbered specs.
- Historical `docs/upgrade-plans/0.15.0–0.21.0.md` and past `INFRA-CHANGELOG.md` entries.
- Speckit-vendored files, `ciliumnetworkpolicy.crd.yaml` (only `minimum` matches), and false positives such as `ApplicationImageDefinition`, `ImageDefinitionImporter`, `mcpProxyDebianImageName`.
- `nvidia.com/gpu` GPU resource handling and DCGM GPU metrics — used by inference deployments, not NIM-specific.

## 1. Code removal

### API / web
- `DeploymentTypeDto.NIM`; `"nim"` subtype registrations in `CreateDeploymentRequestDto`, `DeploymentDto`, `DeploymentInternalDto`.
- Delete `CreateNimDeploymentRequestDto`, `NimDeploymentDto`, `NimDeploymentSourceDto`, `NimDeploymentNgcRegistrySourceDto`, `NimDeploymentInternalDto`.
- Remove NIM branches from `DeploymentDtoMapper`, `ImportConfigDtoMapper`, `ExportConfigMapper`, `ImportConfigValidator`, `DeploymentSourceValidator`; drop `nimDeployments` from `ImportConfigPreviewDto` and `NIM_DEPLOYMENT` from `ExportConfigComponentTypeDto`.
- Delete the storage-size validation chain, used only by `CreateNimDeploymentRequestDto.storageSize`: `ValidStorageSize`, `StorageSizeValidator`, `utils/KubernetesQuantityParser` (no other callers) and their tests; remove `app.validation.resources.max-storage-size` (`RESOURCES_STORAGE_MAX_SIZE`).
- Add a `MethodArgumentTypeMismatchException` → **400** handler to `DefaultExceptionHandler`. Today an unconvertible query param (e.g. `GET /api/v1/deployments?type=NIM` from a not-yet-updated frontend) falls into the catch-all `Exception` handler and returns 500.

### Domain / service
- Delete `NimDeployment`, `CreateNimDeployment`, `NgcRegistrySource` (and its `"ngc_registry"` registration in `Source`), `NimDeploymentManager`, `NimMappers`.
- Remove NIM wiring from `DeploymentManagerProvider`, `WorkloadClassifier` (sealed switch — compiler enforces completeness) and `ProbeConverter`.
- Reword Javadoc that names NIM in `AbstractDeploymentManager`, `AbstractModelDeploymentManager`, `PoolPrimitivesConverter` (KServe remains the only CRD target; the generic converter stays).
- Remove `ExportConfigComponentType.NIM_DEPLOYMENT`, `ActivityResourceType.NimDeployment`, NIM lists/fields in `ExportConfig` and `ImportConfigPreview`, and NIM branches in `ConfigExporter`, `ConfigImporter`, `DeploymentImporter`, `DeploymentImportPreviewer`, `ConfigImportPreviewer`.
- Reword comments/Javadoc that use NIM as an example (e.g. `ServingMetricsCollector`).

### Persistence
- Delete `NimDeploymentEntity`; remove NIM branches from `PersistenceDeploymentMapper`, `DeploymentRepository`, and the entity → resource-type map in `AuditActivityMapper`.

### Kubernetes
- Delete `kubernetes/nim/` (`NimClient`, `K8sNimClient`), `NimServiceInformerRegistration`, `NimServiceEventHandler`, `NimManifestGenerator`, `NimProbeConverter`, `src/main/resources/kubernetes/crd/nimservice.crd.yaml`. Deleting the CRD removes the fabric8 `crd2java`-generated `com.nvidia.apps.v1alpha1.*` classes, so every importer of them must be gone in the same change.
- Remove `K8sResourceKind.NIM_SERVICE` and its handling in `DisposableResourceManager` and `DisposableResourceCleaner` (including the `K8sNimClient` dependency).

### Configuration
- Delete `NimDeployProperties`; remove `nimServiceConfig` / `nimServiceExposeIngressConfig` fields and their `clone*` methods from `AppProperties` (keep `GPU_LIMIT_KEY = "nvidia.com/gpu"` — still used for inference GPUs); remove `k8s.nim.*`, `nim-service-config` and `nim-service-expose-ingress-config` from `application.yml`; remove `K8S_NIM_ENABLED` from `local_env/docker-compose.yml`.
- Leftover `K8S_NIM_*` / `NIM_SERVICE_*` env vars in operator deployments become no-ops (unbound properties are ignored).

## 2. Runtime behaviour after removal

- `POST`/`PUT /api/v1/deployments` with `type: NIM` / `$type: "nim"` (or a `"ngc_registry"` source) fails Jackson deserialization → existing `HttpMessageNotReadableException` handler → **400** `ErrorView` ("Could not resolve type id 'nim' …"). No new handler.
- Config import and import preview with an old ZIP containing a top-level `"nimDeployments"` map: `JsonMapperConfiguration` disables `FAIL_ON_UNKNOWN_PROPERTIES`, so the now-unknown property is ignored. NIM entries are silently dropped; all other components import / preview normally. No code is kept for this.
- Export requests selecting component type `NIM_DEPLOYMENT` → **400** (unknown enum value in the JSON body → `HttpMessageNotReadableException`).
- `GET /api/v1/deployments?type=NIM` → **400** via the new `MethodArgumentTypeMismatchException` handler.
- Activity feed and revision endpoints no longer return `NimDeployment` resources (rows deleted by migration).

## 3. Data migration — `V1.60__RemoveNimDeployment`

One migration per vendor (`H2`, `POSTGRES`, `MS_SQL_SERVER`), SQL unless vendor JSON handling forces a Java migration (follow the existing `db/migration/common/*Base` pattern in that case). Steps, in FK-safe order, single transaction:

1. **Activity feed**: `DELETE FROM audit_activity WHERE resource_type = 'NimDeployment'`.
2. **Audit trail**: delete rows in `deployment_topics_aud` and `deployment_aud` whose id/deployment_id appears in `nim_deployment_aud`; then `DROP TABLE nim_deployment_aud`. `revinfo` rows are kept (revisions may be shared with other entities; empty revisions are harmless to Envers).
3. **Live rows**: `nim_deployment.id` → `deployment.id` is `ON DELETE RESTRICT` and the FK has a different (or generated) name per vendor, so instead of dropping the constraint: copy NIM ids into a staging table `nim_removed_ids`, `DROP TABLE nim_deployment` (its FK goes with it), then delete from `deployment_topics`, `component_removal` (`type = 'DEPLOYMENT'`) and `deployment` where id is in `nim_removed_ids`; finally drop `nim_removed_ids`. No other table references `deployment.id`.
4. **Cleanup queue**: delete `disposable_resource` rows whose `resource_reference` JSON has kind `NIM_SERVICE`. Required: once `K8sResourceKind.NIM_SERVICE` is removed, these rows would fail deserialization in the scheduled `DisposableResourceCleaner`. Vendor-specific text match on the serialized `K8sResourceReference` (`{"$type":"k8s","kind":"NIM_SERVICE",…}`): PostgreSQL `resource_reference::text LIKE '%NIM_SERVICE%'`, H2 `CAST(resource_reference AS VARCHAR) LIKE '%NIM_SERVICE%'`, SQL Server `resource_reference LIKE '%NIM_SERVICE%'` (column is `VARCHAR(MAX)`). Other disposable resources of a NIM deployment's group (secrets, Cilium policies) are kept so the cleaner still removes them.

`docs/db-schema.md` is regenerated by the existing tooling, not hand-edited.

**Kubernetes leftovers**: live `NIMService` CRs and their ingresses are not deleted by the application (the client that could do so is removed). Operators must, **before** upgrading, either undeploy NIM deployments via the API on 0.21.x or run `kubectl get nimservices -A` and delete them.

## 4. Docs and specs

- **Capability specs**: remove NIM requirements/scenarios from `deployments`, `kubernetes-manifests`, `kubernetes-cleanup`, `export-import`, `auditing`, `deployment-metrics`, `image-definitions` (verify each match is genuine before editing).
- **`specs/nim-deployments/spec.md`**: replace with a tombstone — `Status: **Removed** (0.22.0)`, one paragraph on what was removed and why, pointers to `012`, `013`, `015`. Update its row in `specs/README.md` to *Removed*; reword the `kubernetes-manifests` summary row.
- **Agent/contributor docs**: drop NIM mentions from root `CLAUDE.md`, `service/CLAUDE.md`, `kubernetes/CLAUDE.md`, `.specify/memory/constitution.md`, `README.md`.
- **Ops docs**: remove NIM env vars from `docs/configuration.md`; remove NIM sections from `docs/deployment_guide_full_security_model.md`; remove the NIM node from `docs/diagramms/infrastructure_component/infrastrucutre-component-diagramm.drawio`.
- **Upgrade notes**: new `docs/upgrade-plans/0.22.0.md` (breaking API change, frontend must drop NIM type, data deletion, pre-upgrade CR cleanup steps); `docs/INFRA-CHANGELOG.md` 0.22.0 **Removed** section listing every dropped env var.

## 5. Testing

- **New**: functional migration test on H2, PostgreSQL, SQL Server — seed a NIM deployment with topics, audit revisions, an activity row and a `NIM_SERVICE` disposable resource plus a non-NIM deployment; assert V1.60 removes only the NIM data and the app context starts.
- **New**: regression tests — `POST /api/v1/deployments` with `type: NIM` → 400; config import of a ZIP containing a legacy `"nimDeployments"` block plus an MCP deployment → succeeds, MCP deployment imported, no NIM data persisted.
- **Delete**: `NimDeploymentManagerTest`, `NimManifestGeneratorTest`, `NimProbeConverterTest`, `K8sNimClientTest`, `NimServiceEventHandlerTest`, `manifest/nim_service_*.json` fixtures.
- **Update**: remove NIM cases from functional (`DeploymentFunctionalTest`, `ConfigExportImportFunctionalTest`, `DeploymentMetricsFunctionalTest`, `ImageDefinitionFunctionalTest`, …), mapper, validator, node-pool, cleanup and audit tests; `K8sLocalConfiguration`, `FunctionalTestHelper`, `TestSecurityConfig`, `expected_export_config_for_import.json`.
- **ArchUnit**: refresh the frozen violation store via the project's standard freeze workflow, not by hand.
- **Gates**: `./gradlew testFast`, `./gradlew test`, `./gradlew checkstyleMain checkstyleTest`, final `nim` grep audit.

## Risks

- **Frontend breakage**: the client contract changes (enum value and subtype removed). The frontend must ship a matching change; called out in the upgrade plan.
- **Irreversible data loss**: NIM deployments and their audit history are permanently deleted. Accepted.
- **Orphaned cluster resources** if operators skip the pre-upgrade step. Mitigated by upgrade-plan instructions.
