# Data Model: External Registry Reference for Sources

**Feature**: `005-external-registry-ref`
**Date**: 2026-03-14

## New Types

### ExternalRegistryRef (Model layer — interface)

```
Package: com.epam.aidial.deployment.manager.model
File:    model/ExternalRegistryRef.java
```

Polymorphic discriminated interface. Discriminator: `$type`.

| Subtype | Discriminator value | Field | Semantic constraint |
|---------|---------------------|-------|---------------------|
| `McpRegistryRef` | `"mcp-registry"` | `packageName: String` | non-blank (enforced via `@NotBlank` on **DTO layer** only) |
| `GitHubRef` | `"github"` | `repo: String` | non-blank (enforced via `@NotBlank` on **DTO layer** only) |
| `GenericRef` | `"generic"` | `url: String` | non-blank (enforced via `@NotBlank` on **DTO layer** only) |

All subtypes are Java records.

---

### PersistenceExternalRegistryRef (Persistence layer — interface)

```
Package: com.epam.aidial.deployment.manager.dao.entity
File:    dao/entity/PersistenceExternalRegistryRef.java
```

Mirrors the model interface. Same discriminator values. Subtypes: `PersistenceMcpRegistryRef`, `PersistenceGitHubRef`, `PersistenceGenericRef` — all records with identical fields (no Jakarta validation annotations).

---

### ExternalRegistryRefDto (DTO layer — interface)

```
Package: com.epam.aidial.deployment.manager.web.dto
File:    web/dto/ExternalRegistryRefDto.java
```

Mirrors the model interface. Same discriminator values. Subtypes: `McpRegistryRefDto`, `GitHubRefDto`, `GenericRefDto` — all records with `@NotBlank` field validation.

---

## Modified Types

### DockerImageSource (Model — existing class)

```
Package: com.epam.aidial.deployment.manager.model
File:    model/DockerImageSource.java
```

**Added field:**
```java
@Nullable
private ExternalRegistryRef externalRegistryRef;
```

Existing fields unchanged: `imageUri`, `entrypoint`.

---

### GitDockerfileImageSource (Model — existing class)

```
Package: com.epam.aidial.deployment.manager.model
File:    model/GitDockerfileImageSource.java
```

**Added field:**
```java
@Nullable
private ExternalRegistryRef externalRegistryRef;
```

Existing fields unchanged: `url`, `branchName`, `sha`, `baseDirectory`, `entrypoint`.

---

### ImageReferenceSource (Model — existing record)

```
Package: com.epam.aidial.deployment.manager.model.deployment
File:    model/deployment/ImageReferenceSource.java
```

**Record components (after change):**
```java
public record ImageReferenceSource(
    String imageReference,
    @Nullable ExternalRegistryRef externalRegistryRef
) implements Source {}
```

---

### PersistenceDockerImageSource (Persistence — existing record)

```
Package: com.epam.aidial.deployment.manager.dao.entity
```

**Added component:** `PersistenceExternalRegistryRef externalRegistryRef` (nullable, no annotation needed — Jackson handles null).

---

### PersistenceGitDockerfileImageSource (Persistence — existing record)

```
Package: com.epam.aidial.deployment.manager.dao.entity
```

**Added component:** `PersistenceExternalRegistryRef externalRegistryRef` (nullable).

---

### PersistenceImageReferenceSource (Persistence — existing record)

```
Package: com.epam.aidial.deployment.manager.dao.entity.deployment
```

**Record components (after change):**
```java
public record PersistenceImageReferenceSource(
    String imageReference,
    PersistenceExternalRegistryRef externalRegistryRef  // nullable — no annotation needed for Jackson
) implements PersistenceSource {}
```

---

### DockerImageSourceDto (DTO — existing record)

```
Package: com.epam.aidial.deployment.manager.web.dto
```

**Added component:**
```java
@Nullable @Valid ExternalRegistryRefDto externalRegistryRef
```

---

### GitDockerfileImageSourceDto (DTO — existing record)

```
Package: com.epam.aidial.deployment.manager.web.dto
```

**Added component:**
```java
@Nullable @Valid ExternalRegistryRefDto externalRegistryRef
```

---

### ImageReferenceDeploymentSourceDto (DTO — existing record)

```
Package: com.epam.aidial.deployment.manager.web.dto.deployment
```

**Record components (after change):**
```java
public record ImageReferenceDeploymentSourceDto(
    @NotNull String imageReference,
    @Nullable @Valid ExternalRegistryRefDto externalRegistryRef
) implements DeploymentSourceDto {}
```

---

### CreateImageReferenceDeploymentSourceRequestDto (DTO — existing record)

```
Package: com.epam.aidial.deployment.manager.web.dto.deployment
```

**Record components (after change):**
```java
public record CreateImageReferenceDeploymentSourceRequestDto(
    @NotNull String imageReference,
    @Nullable @Valid ExternalRegistryRefDto externalRegistryRef
) implements CreateDeploymentSourceRequestDto {}
```

---

## New Mappers

### ExternalRegistryRefDtoMapper

```
Package: com.epam.aidial.deployment.manager.web.mapper
File:    web/mapper/ExternalRegistryRefDtoMapper.java
Layer:   web
Maps:    ExternalRegistryRefDto ↔ ExternalRegistryRef
```

`@Mapper(componentModel = "spring", subclassExhaustiveStrategy = RUNTIME_EXCEPTION)`

`@SubclassMapping` for all three DTO→model and model→DTO directions.
Handles `null` input → `null` output (MapStruct default).

---

### PersistenceExternalRegistryRefMapper

```
Package: com.epam.aidial.deployment.manager.dao.mapper
File:    dao/mapper/PersistenceExternalRegistryRefMapper.java
Layer:   dao
Maps:    ExternalRegistryRef ↔ PersistenceExternalRegistryRef
```

Same structure. `@SubclassMapping` for all three bidirectional mappings.

---

## Modified Mappers

| Mapper | Change |
|--------|--------|
| `ImageSourceDtoMapper` | Add `ExternalRegistryRefDtoMapper.class` to existing `uses` list; MapStruct auto-maps `externalRegistryRef` field |
| `PersistenceImageSourceMapper` | Add `PersistenceExternalRegistryRefMapper.class` to existing `uses` list |
| `PersistenceDeploymentMapper` | Add `PersistenceExternalRegistryRefMapper.class` to existing `uses` list |
| `DeploymentDtoMapper` | Update `ImageReferenceSource` pattern-match branch to include `externalRegistryRef` component; map via `ExternalRegistryRefDtoMapper` |

---

## Persistence: no migration

`externalRegistryRef` lives inside the existing `source` JSON column on both tables:
- `image_definition.source` (via `ImageDefinitionEntity`)
- `deployment.source` (via `DeploymentEntity`)

No Flyway migration files needed. Jackson deserialises missing fields as `null` for existing rows.

---

## Backward Compatibility

| Scenario | Behaviour |
|----------|-----------|
| Existing row read (no `externalRegistryRef` in JSON) | Field deserialises as `null`; API response omits or returns `null` |
| Existing row write (update without sending `externalRegistryRef`) | Field stays `null` |
| Existing row write (update sending `externalRegistryRef`) | Field is persisted on next save |
| New row created without `externalRegistryRef` | Field is `null` |
