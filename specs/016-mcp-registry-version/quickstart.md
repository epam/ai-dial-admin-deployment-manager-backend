# Quickstart: Add Version to McpRegistryRef

**Feature**: `016-mcp-registry-version`
**Estimated changes**: 3 source files + test updates

## What to change

1. **Add `version` field to 3 records** (one per layer):
   - `McpRegistryRefDto.java` — add `@Nullable @Pattern(regexp = ".*\\S.*", message = "must not be blank") String version`
   - `McpRegistryRef.java` — add `@Nullable String version`
   - `PersistenceMcpRegistryRef.java` — add `String version`

2. **Update tests** to cover version scenarios:
   - `ImageDefinitionFunctionalTest` — create/update with versioned `McpRegistryRef`
   - `DeploymentFunctionalTest` — create/update with versioned `McpRegistryRef`
   - `ConfigExportImportFunctionalTest` — verify version survives export/import round-trip

## What NOT to change

- No MapStruct mapper changes (auto-mapped by field name)
- No database migrations (JSON column, backward compatible)
- No controller changes
- No service layer changes
- No other `ExternalRegistryRef` subtypes

## Verification

```bash
./gradlew checkstyleMain checkstyleTest
./gradlew testFast
```
