# Data Model: Config Import Preview

**Feature**: `007-config-import-preview`
**Date**: 2026-03-16

No database migrations required — this feature is read-only.

---

## New Model Classes (service/model layer)

### `ImportAction` — enum

**Package**: `model.config`

```
CREATE  — entity does not exist in DB; will be inserted
UPDATE  — entity exists; policy=OVERWRITE; will be overwritten
SKIP    — entity exists; policy=SKIP_IF_EXISTS; will not be touched
FAIL    — entity exists; policy=FAIL_IF_EXISTS; import would throw
```

---

### `ImportComponent<T>` — generic wrapper

**Package**: `model.config`
**Style**: `@Data @AllArgsConstructor @NoArgsConstructor` (Lombok mutable, for MapStruct compat)

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `action` | `ImportAction` | No | Predicted outcome |
| `prev` | `T` | Yes | Existing entity state; `null` for CREATE |
| `next` | `T` | Yes | Incoming entity state; `null` for SKIP |

---

### `ImportConfigPreview` — aggregate model

**Package**: `model.config`
**Style**: `@Data @Builder @AllArgsConstructor @NoArgsConstructor` (Lombok)

| Field | Type | Notes |
|-------|------|-------|
| `mcpImageDefinitions` | `List<ImportComponent<McpImageDefinition>>` | `@Builder.Default = new ArrayList<>()` |
| `adapterImageDefinitions` | `List<ImportComponent<AdapterImageDefinition>>` | `@Builder.Default = new ArrayList<>()` |
| `interceptorImageDefinitions` | `List<ImportComponent<InterceptorImageDefinition>>` | `@Builder.Default = new ArrayList<>()` |
| `globalImageBuildDomainWhitelist` | `ImportComponent<List<String>>` | Nullable; absent when incoming list is empty/null |
| `mcpDeployments` | `List<ImportComponent<McpDeployment>>` | `@Builder.Default = new ArrayList<>()` |
| `adapterDeployments` | `List<ImportComponent<AdapterDeployment>>` | `@Builder.Default = new ArrayList<>()` |
| `interceptorDeployments` | `List<ImportComponent<InterceptorDeployment>>` | `@Builder.Default = new ArrayList<>()` |
| `nimDeployments` | `List<ImportComponent<NimDeployment>>` | `@Builder.Default = new ArrayList<>()` |
| `inferenceDeployments` | `List<ImportComponent<InferenceDeployment>>` | `@Builder.Default = new ArrayList<>()` |

---

## New DTO Classes (web layer)

### `ImportActionDto` — enum DTO

**Package**: `web.dto.config`

Values: `CREATE`, `UPDATE`, `SKIP`, `FAIL` — mirrors `ImportAction` 1:1.

---

### `ImportComponentDto<T>` — generic DTO record

**Package**: `web.dto.config`

```java
public record ImportComponentDto<T>(ImportActionDto importAction, T prev, T next) {}
```

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `importAction` | `ImportActionDto` | No | Predicted outcome |
| `prev` | `T` | Yes | Existing entity DTO; `null` for CREATE |
| `next` | `T` | Yes | Incoming entity DTO; `null` for SKIP |

---

### `ImportConfigPreviewDto` — response record

**Package**: `web.dto.config`

| Field | Type | Notes |
|-------|------|-------|
| `mcpImageDefinitions` | `List<ImportComponentDto<McpImageDefinitionDto>>` | |
| `adapterImageDefinitions` | `List<ImportComponentDto<AdapterImageDefinitionDto>>` | |
| `interceptorImageDefinitions` | `List<ImportComponentDto<InterceptorImageDefinitionDto>>` | |
| `globalImageBuildDomainWhitelist` | `ImportComponentDto<List<String>>` | Nullable |
| `mcpDeployments` | `List<ImportComponentDto<McpDeploymentDto>>` | |
| `adapterDeployments` | `List<ImportComponentDto<AdapterDeploymentDto>>` | |
| `interceptorDeployments` | `List<ImportComponentDto<InterceptorDeploymentDto>>` | |
| `nimDeployments` | `List<ImportComponentDto<NimDeploymentDto>>` | |
| `inferenceDeployments` | `List<ImportComponentDto<InferenceDeploymentDto>>` | |

---

## Conflict Resolution Semantics (all entity types)

| DB state | `ConflictResolutionPolicy` | `ImportAction` | `prev` | `next` |
|----------|---------------------------|----------------|--------|--------|
| Not found | any | `CREATE` | `null` | incoming entity |
| Found | `OVERWRITE` | `UPDATE` | existing entity | incoming entity |
| Found | `SKIP_IF_EXISTS` | `SKIP` | existing entity | `null` |
| Found | `FAIL_IF_EXISTS` | `FAIL` | existing entity | incoming entity |

### Image definition conflict key
`(ImageType, name, version)` — not the UUID. Matches `ImageDefinitionImporter` lookup:
`imageDefinitionService.getImageDefinitionByTypeAndNameAndVersion(type, name, version)`

### Deployment conflict key
String `id` — matches `DeploymentImporter` lookup: `deploymentService.getDeployment(id, false)`

### Whitelist conflict condition
Whitelist entity exists (no `GlobalDomainWhitelistNotFoundException`) and incoming list is non-empty.
When incoming list is empty or null → no `ImportComponent` emitted.
When whitelist does not exist → `CREATE`.
