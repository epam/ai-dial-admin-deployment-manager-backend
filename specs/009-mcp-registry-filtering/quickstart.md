# Quickstart: MCP Registry Backend Filtering

## What this feature does

Adds backend-side filtering to the MCP registry server list endpoints. The upstream MCP registry only supports name search — this feature adds filtering by remote transport type, package registry type, and repository existence by scanning multiple upstream pages and filtering in memory.

## Key files to modify

| File | Change |
|------|--------|
| `registry/mcp/web/dto/ServersRequestDto.java` | Add `filter` field (`ServerFilterDto`) |
| `registry/mcp/web/dto/ServerFilterDto.java` | **NEW** — filter criteria DTO |
| `registry/mcp/web/controller/McpRegistryController.java` | Add filter query params to GET endpoint, construct filter DTO |
| `registry/mcp/service/McpRegistryService.java` | Add multi-page scanning loop with filtering |
| `registry/mcp/service/McpServerFilter.java` | **NEW** — filter predicate logic |
| `registry/mcp/properties/McpRegistryProperties.java` | Add `maxPagesToScan` field |
| `src/main/resources/application.yml` | Add `max-pages-to-scan` config |
| `docs/configuration.md` | Document new env var |

## Key files to add/modify for tests

| File | Change |
|------|--------|
| `mcpregistry/service/McpServerFilterTest.java` | **NEW** — unit tests for filter predicate |
| `mcpregistry/service/McpRegistryServiceTest.java` | **NEW** — unit tests for scanning loop |
| `mcpregistry/web/controller/McpRegistryControllerTest.java` | Add tests for filter params |
| `src/test/resources/mcp-registry/` | Add JSON fixtures for filtered scenarios |

## Implementation order

1. **ServerFilterDto** — new DTO (no dependencies)
2. **McpServerFilter** — filter predicate + unit tests (depends on DTO)
3. **McpRegistryService** — scanning loop + unit tests (depends on filter + client)
4. **ServersRequestDto** — add `filter` field
5. **McpRegistryController** — add GET params, wire filter to service
6. **McpRegistryProperties** + `application.yml` — config property
7. **Controller tests** — integration tests with mocked service
8. **docs/configuration.md** — document new env var

## How to verify

```bash
# Run fast tests (H2 only)
./gradlew testFast

# Run checkstyle
./gradlew checkstyleMain checkstyleTest

# Full build
./gradlew clean build
```
