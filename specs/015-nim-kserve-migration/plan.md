# Implementation Plan: NIM KServe Migration & Configurable Storage Size

**Branch**: `019-nim-kserve-migration` | **Date**: 2026-04-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/015-nim-kserve-migration/spec.md`
**Status**: Implemented (post-factum documentation)

## Summary

This feature migrates NIM deployments from standalone inference platform with nginx ingress to kserve inference platform with Knative autoscaling, and adds a configurable `storageSize` field so operators can set PVC size per NIM deployment instead of using the hardcoded 20Gi default.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2, NVIDIA NIM CRD (NIMService), MapStruct 1.6.0, Lombok 8.10
**Storage**: H2 (dev/test), PostgreSQL, SQL Server — Flyway migrations
**Testing**: JUnit 5, Mockito, AssertJ, Testcontainers
**Target Platform**: Kubernetes cluster with Knative Serving and NVIDIA NIM operator
**Project Type**: Web service (Spring Boot backend)

## Constitution Check

*GATE: All checks passed.*

| Rule | Status | Notes |
|------|--------|-------|
| Layered Architecture | PASS | Validator in `web/validation/`, parser utility in `utils/`, manifest logic in `service/manifest/`, K8s types in `kubernetes/` |
| Kubernetes Isolation | PASS | `KubernetesQuantityParser` uses Fabric8 `Quantity` from `utils/` (not `kubernetes/`); web layer accesses it through the utility, not directly |
| Architecture Test | PASS | Web layer does not import `io.fabric8.kubernetes.*` directly — uses `KubernetesQuantityParser` indirection |
| Naming Conventions | PASS | `NimDeploymentEntity`, `NimDeployment`, `CreateNimDeploymentRequestDto`, `NimDeploymentDto`, `StorageSizeValidator`, `ValidStorageSize` |
| MapStruct componentModel | PASS | All mappers use `componentModel = "spring"` |
| Flyway migrations | PASS | V1.57 created in all 3 vendor dirs (H2, POSTGRES, MS_SQL_SERVER) |
| Config property defaults | PASS | Default `200Gi` declared in `application.yml` via `${RESOURCES_STORAGE_MAX_SIZE:200Gi}` |
| Configuration docs | PASS | `docs/configuration.md` updated with `app.validation.resources.max-storage-size` |
| Code style | PASS | Checkstyle passes on main and test sources |

## Project Structure

### Documentation (this feature)

```text
specs/015-nim-kserve-migration/
├── plan.md              # This file
├── spec.md              # Feature specification
└── checklists/
    └── requirements.md  # Quality checklist
```

### Source Code (affected files)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── dao/
│   ├── entity/deployment/
│   │   └── NimDeploymentEntity.java          # +storageSize column
│   └── mapper/
│       └── PersistenceDeploymentMapper.java   # +storageSize in NIM update block
├── model/deployment/
│   ├── NimDeployment.java                     # +storageSize field
│   └── CreateNimDeployment.java               # +storageSize field
├── service/
│   ├── deployment/
│   │   └── NimDeploymentManager.java          # Extract & pass storageSize
│   └── manifest/
│       └── NimManifestGenerator.java          # +storageSize param, applyStorageSize()
├── utils/
│   ├── KubernetesQuantityParser.java          # NEW: Fabric8 Quantity wrapper
│   └── mapping/
│       └── NimMappers.java                    # +Storage/Pvc field mappers
└── web/
    ├── dto/deployment/
    │   ├── CreateNimDeploymentRequestDto.java # +@ValidStorageSize storageSize
    │   └── NimDeploymentDto.java              # +storageSize field
    └── validation/
        ├── ValidStorageSize.java              # NEW: constraint annotation
        └── StorageSizeValidator.java          # NEW: Fabric8 Quantity-based validation

src/main/resources/
├── application.yml                            # +max-storage-size config
└── db/migration/
    ├── H2/V1.57__AddStorageSizeToNimDeployment.sql
    ├── POSTGRES/V1.57__AddStorageSizeToNimDeployment.sql
    └── MS_SQL_SERVER/V1.57__AddStorageSizeToNimDeployment.sql

src/test/java/com/epam/aidial/deployment/manager/
├── utils/
│   └── KubernetesQuantityParserTest.java      # NEW: parser unit tests
├── web/validation/
│   └── StorageSizeValidatorTest.java          # NEW: validator unit tests
└── service/
    ├── deployment/
    │   └── NimDeploymentManagerTest.java       # Updated mock signatures
    └── manifest/
        └── NimManifestGeneratorTest.java       # Updated calls + storage tests

docs/
├── configuration.md                           # +max-storage-size property
└── db-schema.md                               # Auto-updated by hook

specs/nim-deployments/spec.md                  # +storageSize requirement
```

## Design Decisions

### 1. storageSize as String (not Long)

**Decision**: Store and transport `storageSize` as a String in Kubernetes quantity format (e.g., `"50Gi"`, `"21474836480"`).

**Rationale**: The NIM CRD's `Pvc.size` field is a String. Operators think in Gi/Ti units. Avoiding bytes-to-string conversion at manifest generation time eliminates lossy conversion issues.

### 2. Fabric8 Quantity parser for validation

**Decision**: Use `io.fabric8.kubernetes.api.model.Quantity` (already a project dependency) for parsing and validation instead of custom regex.

**Rationale**: Fabric8 Quantity handles all Kubernetes quantity formats (binary/decimal suffixes, plain bytes, fractional values). Wrapped in `KubernetesQuantityParser` utility to avoid architecture rule violation (web layer cannot import `io.fabric8.kubernetes.*`).

### 3. Configurable upper bound with default

**Decision**: Max storage size configured at `app.validation.resources.max-storage-size` (default `200Gi`), grouped under existing `validation.resources` config namespace.

**Rationale**: Consistent with existing resource validation properties (`max-cpu-in-cores`, `max-memory-in-mb`, `max-nvidia-gpu`). The 200Gi default prevents accidental over-provisioning.

### 4. Template-based override pattern

**Decision**: When `storageSize` is non-null, override `spec.storage.pvc.size` on the cloned NIM template. When null, preserve the template default (20Gi).

**Rationale**: Follows the same pattern as `containerGrpcPort` — nullable field that overrides the template when present. No breaking change for existing deployments.

## Complexity Tracking

No constitution violations. No complexity justifications needed.
