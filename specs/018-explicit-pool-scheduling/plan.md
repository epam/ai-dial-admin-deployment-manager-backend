# Implementation Plan: Explicit Node Pool Scheduling

**Branch**: `018-explicit-pool-scheduling` | **Date**: 2026-05-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/018-explicit-pool-scheduling/spec.md`

## Summary

Replace Feature 016's derived-label-key + per-pool capacity-numbers configuration with **explicit Kubernetes scheduling primitives per pool** (`nodeSelector`, `affinity`, `tolerations`) and add two startup-validated default environment variables (`NODE_POOL_DEFAULT`, `NODE_POOL_DEFAULT_MODEL`) that stamp onto new deployments via a create-time cascade. `NODE_POOLS` is consumed as a YAML document (JSON-subset compatible) in strict mode. Existing `node_pool` column on `deployment` is retained; existing `NodePoolService` / `NodePoolController` / `NodePoolDtoMapper` are reshaped rather than rebuilt. Manifest generators already accept pool data; their signatures change to carry the full primitive set instead of a label-key map. Defaults are resolved once at create time and persisted onto the deployment record (FR-018, FR-019) — never re-resolved at deploy or update, so users always see the actual pool and admin env-var changes do not silently migrate existing deployments. Explicit `nodePool: null` is honoured as "Any" (no pool primitives). Export omits `nodePool`; import ignores it (FR-021).

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2 (provides `Affinity`, `Toleration`, `NodeSelector*` API model classes — used directly as the schema for `NODE_POOLS` primitives), Jackson 2.21.1 + `jackson-dataformat-yaml` (already on the classpath via Spring Boot's `spring-boot-starter`), MapStruct 1.6.0, Lombok 8.10, SpringDoc OpenAPI 2.8.5, Hibernate Validator (Jakarta Validation)
**Storage**: H2 / PostgreSQL / SQL Server — existing `deployment.node_pool` VARCHAR(255) column from V1.58 (Feature 016) is reused as-is; no migration needed
**Testing**: JUnit 5 + AssertJ; `./gradlew testFast` (H2) during development; full suite (Testcontainers Postgres + SQL Server) via `./gradlew test`
**Target Platform**: Linux server (Spring Boot service deployed into Kubernetes)
**Project Type**: Web service (single Spring Boot project — extend in place)
**Performance Goals**: Validation runs once at startup; create-time cascade is a constant-time lookup against in-memory config; no per-request K8s API calls in the pool-resolution path
**Constraints**: Constitution Principle I (web → service → dao/kubernetes); Principle III (no Fabric8 types outside `kubernetes/` in non-configuration code — `configuration/` is the documented exception where Fabric8 model types live alongside `@ConfigurationProperties`); strict YAML parsing must reject deprecated fields (`maxNodes`, `cpu`, `memory`, `gpu`, `NODE_POOL_LABEL_KEY`); `application.yml` and env vars MUST both be bindable per FR-022
**Scale/Scope**: Pool count is a small finite number (single-digit to low double-digit per environment); deployments per environment are O(10²–10³); no per-request cost matters

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Layered architecture (web → service → dao/kubernetes) | PASS | `NodePoolController` (web) → `NodePoolService` (service) → `NodePoolProperties` (configuration); manifest generators (`service/manifest`) consume pool primitives; Fabric8 model types confined to `configuration/` (schema for YAML deserialization) and `service/manifest/` (where they were already used for primitive projection) |
| `@Transactional` only on service/dao | PASS | Pool resolution is in-memory config lookup — no transactional concern; `DeploymentService` create/update already carries `@Transactional` and gains the cascade call inside its existing transaction |
| K8s isolation in `kubernetes/` package | PASS | No new K8s API calls. Pool primitives are static config; they are injected into manifest generators that already live in `service/manifest/` and pass primitives to the existing `kubernetes/` projection layer |
| `@LogExecution` on all Spring components | PASS | Existing `NodePoolService` / `NodePoolController` already annotated; new `NodePoolValidationService` (startup validator) and `NodePoolResolutionService` (create-time cascade) MUST be annotated |
| Naming conventions | PASS | `NodePoolDto`, `NodePoolDtoMapper`, `NodePoolController`, `NodePoolService`, `NodePoolProperties`, `NodePoolConfiguration` — all existing names retained, schemas updated in place |
| Code style (Google Java, 180-char, -Werror) | PASS | Enforced by Checkstyle on every build |
| Config defaults in application.yml only (Constitution PATCH 1.2.2) | PASS | `NodePoolProperties` fields declared without initializers; all defaults appear only in `application.yml` via `${ENV_VAR:default}` syntax |
| Flyway owns schema | PASS | `deployment.node_pool` column already exists from V1.58 — no migration required; `docs/db-schema.md` regeneration not needed for this feature |
| No business logic in entities | PASS | `DeploymentEntity.nodePool` remains a plain `String` field; cascade logic lives in `NodePoolResolutionService` |
| OpenAPI annotations on endpoints | PASS | `NodePoolController.list()` keeps `@Operation` + `@ApiResponse`; updated schema reflected in the response DTO's `@Schema` annotations |
| `docs/configuration.md` updated | PENDING | Plan includes mandatory task to rewrite the Node Pool Configuration section per the Documentation Deliverables in spec |
| Configuration property defaults — single source of truth | PASS | `NodePoolProperties` will NOT carry Java field initializers; defaults supplied via `application.yml` (`${NODE_POOLS:}`, `${NODE_POOL_DEFAULT:}`, `${NODE_POOL_DEFAULT_MODEL:}`) |
| Anti-pattern: no polling loops | PASS | This feature introduces no K8s watching; existing informer patterns untouched |
| Anti-pattern: no generic exception catch | PASS | Configuration parsing errors are caught as specific Jackson exceptions and rethrown as `IllegalArgumentException` with field-path context |

All gates PASS. No deviations requiring justification in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/018-explicit-pool-scheduling/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 — decisions on YAML parsing, Fabric8 deserialization, listing-API shape
├── data-model.md        # Phase 1 — entities and Java type layout
├── quickstart.md        # Phase 1 — operator/dev smoke test
├── contracts/
│   └── node-pools-api.md   # Phase 1 — GET /api/v1/node-pools response shape; create/update validation contract
└── checklists/
    └── requirements.md  # Spec quality checklist (already present from /speckit.specify)
```

### Source Code (repository root)

The feature is a single Spring Boot service. The layout below shows only the files touched by Feature 018. Files marked **MODIFIED** are existing Feature 016 artefacts whose schema or behaviour changes; files marked **NEW** are introduced by this feature.

```text
src/main/java/com/epam/aidial/deployment/manager/
├── configuration/
│   ├── NodePoolProperties.java                          # MODIFIED — drop maxNodes/cpu/memory/gpu/labelKey; add nodeSelector/affinity/tolerations + default fields
│   └── NodePoolConfiguration.java                       # MODIFIED — switch JSON → YAML, strict parsing, validate defaults reference existing pools at startup
├── web/
│   ├── controller/
│   │   └── NodePoolController.java                      # MODIFIED — extend response with top-level defaults block (FR-017)
│   ├── dto/
│   │   └── nodepool/
│   │       ├── NodePoolDto.java                         # MODIFIED — drop capacity fields, add nodeSelector/affinity/tolerations
│   │       ├── NodePoolListResponseDto.java             # NEW — wraps {pools: […], defaults: {default, model}}
│   │       ├── NodeSchedulingPrimitivesDto.java         # NEW — subset projection of nodeSelector/affinity/tolerations for the response
│   │       └── (existing GpuSpecDto/CpuSpecDto/MemorySpecDto deleted)
│   └── mapper/
│       └── NodePoolDtoMapper.java                       # MODIFIED — map new shape; produce NodePoolListResponseDto
├── service/
│   ├── nodepool/
│   │   ├── NodePoolService.java                         # MODIFIED — getNodePools() returns new shape; add getDefaults(); add resolveForCreate(DeploymentType, String suppliedNodePool, boolean explicitlySet)
│   │   └── NodePoolValidationService.java               # NEW — startup validator: checks NODE_POOL_DEFAULT and NODE_POOL_DEFAULT_MODEL reference existing pools (FR-015); invoked from NodePoolConfiguration after pool loading
│   ├── deployment/
│   │   └── DeploymentService.java                       # MODIFIED — call NodePoolService.resolveForCreate(...) on create; pass-through on update (no cascade) (FR-013, FR-014, FR-018, FR-019)
│   ├── manifest/
│   │   ├── KnativeManifestGenerator.java                # MODIFIED — change input from Map<String,String> nodePoolLabels to PoolSchedulingPrimitives (nodeSelector + affinity + tolerations)
│   │   ├── NimManifestGenerator.java                    # MODIFIED — same signature change
│   │   └── InferenceManifestGenerator.java              # MODIFIED — same signature change
│   └── exportimport/
│       └── DeploymentExportService.java                 # MODIFIED — strip nodePool from export payload (FR-021); import path ignores any incoming nodePool field
├── dao/
│   └── entity/deployment/
│       └── DeploymentEntity.java                        # UNCHANGED — node_pool column already present from V1.58
└── service/duplication/                                 # if duplication lives in its own package
    └── DeploymentDuplicationService.java                # MODIFIED — pass source's stored nodePool verbatim through to create-path WITHOUT running cascade (FR-020)

src/main/resources/
├── application.yml                                      # MODIFIED — drop label-key; add default + default-model under app.node-pools.*

src/test/java/com/epam/aidial/deployment/manager/
├── configuration/
│   └── NodePoolConfigurationTest.java                   # MODIFIED — exercise YAML parsing, strict mode, deprecated-field rejection, defaults-validation startup error
├── service/nodepool/
│   ├── NodePoolServiceTest.java                         # MODIFIED — cover cascade resolution for the four configuration combinations
│   └── NodePoolValidationServiceTest.java               # NEW
├── service/deployment/
│   └── DeploymentServiceTest.java                       # MODIFIED — create-time cascade behaviour; explicit null honoured; duplicate copies verbatim
├── service/manifest/
│   ├── KnativeManifestGeneratorTest.java                # MODIFIED — verify primitives projection (nodeSelector + affinity + tolerations on the same workload)
│   ├── NimManifestGeneratorTest.java                    # MODIFIED — same
│   └── InferenceManifestGeneratorTest.java              # MODIFIED — same
└── web/controller/
    └── NodePoolControllerTest.java                      # MODIFIED — assert new response shape + defaults block

docs/
└── configuration.md                                     # MODIFIED — rewrite Node Pool Configuration section per Documentation Deliverables in spec
```

**Structure Decision**: Single Spring Boot project, layered per constitution. Pool primitives (Fabric8 model classes) live in `configuration/` (where they're loaded) and `service/manifest/` (where they're projected onto workloads) — both are permissible under Principle III, which scopes the Fabric8 restriction to "non-configuration code". The new `NodePoolValidationService` and `NodePoolResolutionService` (combined into the extended `NodePoolService` to keep the count down) handle the create-time cascade. No new controller endpoints; the existing `GET /api/v1/node-pools` is reshaped per FR-011 and FR-017.

## Complexity Tracking

No constitutional violations require justification. This feature is largely a schema migration of existing Feature 016 components plus net-new but well-scoped create-time stamping logic.
