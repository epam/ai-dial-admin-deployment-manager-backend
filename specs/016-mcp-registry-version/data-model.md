# Data Model: Add Version to McpRegistryRef

**Feature**: `016-mcp-registry-version`
**Date**: 2026-04-10

## Modified Types

### McpRegistryRef (Model layer — record)

```
Package: com.epam.aidial.deployment.manager.model
File:    model/McpRegistryRef.java
```

**Record components (after change):**
```java
public record McpRegistryRef(
    String packageName,
    @Nullable String version
) implements ExternalRegistryRef {}
```

- `packageName`: String, non-blank, required — identifies an MCP registry package (unchanged)
- `version`: String, nullable, optional — identifies a specific published version of the package (new)

---

### McpRegistryRefDto (DTO layer — record)

```
Package: com.epam.aidial.deployment.manager.web.dto
File:    web/dto/McpRegistryRefDto.java
```

**Record components (after change):**
```java
public record McpRegistryRefDto(
    @NotBlank String packageName,
    @Nullable @Pattern(regexp = ".*\\S.*", message = "must not be blank") String version
) implements ExternalRegistryRefDto {}
```

- `packageName`: `@NotBlank`, required (unchanged)
- `version`: `@Nullable @Pattern(regexp = ".*\\S.*")`, optional — null is valid; when provided, must contain at least one non-whitespace character (new)

Validation behavior:
- `null` → valid (`@Pattern` skips null per Jakarta Bean Validation spec)
- `""` → invalid (rejected by pattern)
- `"  "` → invalid (rejected by pattern)
- `"1.0.0"` → valid

---

### PersistenceMcpRegistryRef (Persistence layer — record)

```
Package: com.epam.aidial.deployment.manager.dao.entity
File:    dao/entity/PersistenceMcpRegistryRef.java
```

**Record components (after change):**
```java
public record PersistenceMcpRegistryRef(
    String packageName,
    String version
) implements PersistenceExternalRegistryRef {}
```

- `packageName`: String (unchanged)
- `version`: String, nullable (Jackson handles null naturally) (new)

No validation annotations at persistence layer — consistent with existing pattern.

---

## Mapper Changes

**No mapper changes needed.** MapStruct auto-maps fields with matching names. The existing `ExternalRegistryRefDtoMapper` and `PersistenceExternalRegistryRefMapper` use `@SubclassMapping` which delegates to MapStruct's default record-to-record mapping for each subtype. Adding `version` to all three `McpRegistryRef*` records means MapStruct automatically includes it in generated code.

---

## Persistence: No Migration

The `version` field lives inside the JSON blob of `externalRegistryRef`, which itself is nested in the `source` JSON column on both tables:
- `image_definition.source` (JSON/JSONB)
- `deployment.source` (JSON/JSONB)

No Flyway migration files needed. Jackson deserializes missing fields as `null` for existing rows.

---

## Backward Compatibility

| Scenario | Behavior |
|----------|----------|
| Existing row with `McpRegistryRef` (no `version` in JSON) | `version` deserializes as `null`; API response omits or returns `null` |
| New row with `McpRegistryRef` and `version` | `version` is serialized in JSON and returned in API responses |
| Update existing `McpRegistryRef` to add `version` | `version` is persisted on next save |
| Update existing `McpRegistryRef` to remove `version` (send without it) | `version` becomes `null` on next save |
