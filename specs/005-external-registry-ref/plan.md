# Implementation Plan: External Registry Reference for Sources

**Branch**: `005-external-registry-ref` | **Date**: 2026-03-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/005-external-registry-ref/spec.md`

## Summary

Add an optional `externalRegistryRef` polymorphic field to three "general" source types (`DockerImageSource`, `GitDockerfileImageSource`, `ImageReferenceSource`). The field is a discriminated union (`McpRegistryRef`, `GitHubRef`, `GenericRef`) stored within the existing `source` JSON column — no schema migration required. The change spans three layers (DTO, model, persistence) plus two new MapStruct mapper chains, with no changes to service logic, K8s integration, or export/import infrastructure.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Jackson 2.21.1 (polymorphic discriminators), MapStruct 1.6.0, Lombok 8.10, Jakarta Validation, SpringDoc OpenAPI 2.8.5
**Storage**: H2 / PostgreSQL / SQL Server — JSON column (`source`) on `image_definition` and `deployment` tables; **no Flyway migration needed**
**Testing**: JUnit 5, AssertJ, Testcontainers — functional tests extend `H2FunctionalTests` / `PostgresFunctionalTests` / `SqlServerFunctionalTests`
**Target Platform**: Linux server (Spring Boot REST service)
**Project Type**: Web service (REST API backend)
**Performance Goals**: No new performance requirements — metadata-only field addition to existing CRUD operations
**Constraints**: Backward-compatible; existing rows without `externalRegistryRef` must deserialise cleanly to `null`
**Scale/Scope**: Same as existing image definition and deployment APIs

## Constitution Check

| Rule | Status | Notes |
|------|--------|-------|
| Strict layered architecture | ✅ | web → service (unchanged) → dao. No layer shortcuts |
| `@Transactional` discipline | ✅ | No new transactions; service layer untouched |
| Kubernetes isolation | ✅ | No K8s or Knative code touched |
| `@LogExecution` on Spring components | ✅ | No new `@Service`/`@Component`/`@Controller` classes introduced |
| MapStruct `componentModel = "spring"`, `subclassExhaustiveStrategy` | ✅ | Applied on all new mapper interfaces |
| Naming conventions | ✅ | `ExternalRegistryRefDto`, `*DtoMapper`, `Persistence*Mapper`, `*Ref` records |
| Google Java Style, 180-char lines | ✅ | Enforced by `./gradlew checkstyleMain` |
| No wildcard imports | ✅ | Enforced by Checkstyle |
| Flyway owns schema | ✅ | No `ddl-auto` change; JSON column stores new field without migration |
| `docs/configuration.md` update | ✅ N/A | No new `@ConfigurationProperties` or env vars introduced |
| `docs/db-schema.md` regeneration | ✅ N/A | No migration files added |

**No violations. No complexity tracking table needed.**

## Project Structure

### Documentation (this feature)

```text
specs/005-external-registry-ref/
├── plan.md              ← this file
├── research.md          ← Phase 0 decisions
├── data-model.md        ← Phase 1 entity model
├── quickstart.md        ← Phase 1 usage examples
├── contracts/
│   ├── external-registry-ref-type.md    ← ExternalRegistryRefDto union
│   └── modified-source-dtos.md         ← changed source request/response shapes
└── tasks.md             ← Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/

model/
├── ExternalRegistryRef.java              NEW  interface — polymorphic discriminator
├── McpRegistryRef.java                   NEW  record — { packageName }
├── GitHubRef.java                        NEW  record — { repo }
├── GenericRef.java                       NEW  record — { url }
├── DockerImageSource.java                MOD  add @Nullable ExternalRegistryRef field
└── GitDockerfileImageSource.java         MOD  add @Nullable ExternalRegistryRef field

model/deployment/
└── ImageReferenceSource.java             MOD  add ExternalRegistryRef component to record

dao/entity/
├── PersistenceExternalRegistryRef.java   NEW  interface
├── PersistenceMcpRegistryRef.java        NEW  record
├── PersistenceGitHubRef.java             NEW  record
├── PersistenceGenericRef.java            NEW  record
├── PersistenceDockerImageSource.java     MOD  add PersistenceExternalRegistryRef component
└── PersistenceGitDockerfileImageSource.java  MOD  add PersistenceExternalRegistryRef component

dao/entity/deployment/
└── PersistenceImageReferenceSource.java  MOD  add PersistenceExternalRegistryRef component

dao/mapper/
├── PersistenceExternalRegistryRefMapper.java  NEW  @SubclassMapping bidirectional
├── PersistenceImageSourceMapper.java          MOD  add uses = PersistenceExternalRegistryRefMapper
└── PersistenceDeploymentMapper.java           MOD  add uses = PersistenceExternalRegistryRefMapper

web/dto/
├── ExternalRegistryRefDto.java           NEW  interface
├── McpRegistryRefDto.java                NEW  record — { @NotBlank packageName }
├── GitHubRefDto.java                     NEW  record — { @NotBlank repo }
├── GenericRefDto.java                    NEW  record — { @NotBlank url }
├── DockerImageSourceDto.java             MOD  add @Nullable @Valid ExternalRegistryRefDto
└── GitDockerfileImageSourceDto.java      MOD  add @Nullable @Valid ExternalRegistryRefDto

web/dto/deployment/
├── ImageReferenceDeploymentSourceDto.java          MOD  add @Nullable @Valid field
└── CreateImageReferenceDeploymentSourceRequestDto.java  MOD  add @Nullable @Valid field

web/mapper/
├── ExternalRegistryRefDtoMapper.java     NEW  @SubclassMapping bidirectional
├── ImageSourceDtoMapper.java             MOD  add uses = ExternalRegistryRefDtoMapper
└── DeploymentDtoMapper.java             MOD  update ImageReferenceSource switch pattern

src/test/java/com/epam/aidial/deployment/manager/
functional/h2/
└── (existing functional test files)      MOD  add test scenarios for externalRegistryRef
```

**Structure Decision**: Single-project layout (existing). All changes are additions to existing packages; no new packages created.

## Phase 0 Artifacts

→ [research.md](research.md) — all technical decisions resolved; no NEEDS CLARIFICATION remaining.

## Phase 1 Artifacts

→ [data-model.md](data-model.md) — complete entity model, modified types, new mappers
→ [contracts/external-registry-ref-type.md](contracts/external-registry-ref-type.md) — ExternalRegistryRefDto discriminated union
→ [contracts/modified-source-dtos.md](contracts/modified-source-dtos.md) — changed source request/response shapes
→ [quickstart.md](quickstart.md) — usage examples for all source types

## Key Implementation Notes

### MapStruct auto-mapping chain

When `ExternalRegistryRefDtoMapper` is added to `uses` on `ImageSourceDtoMapper`, MapStruct will automatically map the `externalRegistryRef` field when mapping `DockerImageSourceDto ↔ DockerImageSource` and `GitDockerfileImageSourceDto ↔ GitDockerfileImageSource`. No `@Mapping` annotation needed — field names match.

Same applies to `PersistenceImageSourceMapper` with `PersistenceExternalRegistryRefMapper`.

### DeploymentDtoMapper manual switch

The `toDeploymentSourceDto` method uses Java 21 record pattern destructuring. Update:

```java
// Before
case ImageReferenceSource(String imageReference) ->
    new ImageReferenceDeploymentSourceDto(imageReference);

// After
case ImageReferenceSource(String imageReference, ExternalRegistryRef externalRegistryRef) ->
    new ImageReferenceDeploymentSourceDto(
        imageReference,
        externalRegistryRefDtoMapper.toDto(externalRegistryRef)
    );
```

`ExternalRegistryRefDtoMapper` is injected via `@Autowired` or `@Spy` (MapStruct Spring component).

### Construction sites for ImageReferenceSource

All existing `new ImageReferenceSource(imageReference)` calls (primarily in `DeploymentDtoMapper.applyCreateImageSource`) must be updated to pass the `externalRegistryRef` second component — either from the create request DTO (if provided) or `null`.

### No service or K8s layer changes

`ImageDefinitionService`, `DeploymentService`, `ConfigExporter`, `DeploymentImporter`, and all Kubernetes/Knative classes are unchanged. The `externalRegistryRef` is transparent to business logic.

### Export/import: automatic

The `externalRegistryRef` is serialised as part of the `source` JSON object. The existing `ConfigExporter` and `DeploymentImporter` preserve the full source object through the serialisation chain without any changes.

### Backward compatibility in persistence

`PersistenceDockerImageSource`, `PersistenceGitDockerfileImageSource`, and `PersistenceImageReferenceSource` are Java records. Adding a new component to a record is a source-level change only — it does not alter the database schema. Jackson deserialises missing JSON fields as `null` for existing rows, satisfying backward compatibility.
