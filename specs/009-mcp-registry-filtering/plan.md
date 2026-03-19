# Implementation Plan: MCP Registry Backend Filtering

**Branch**: `009-mcp-registry-filtering` | **Date**: 2026-03-18 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-mcp-registry-filtering/spec.md`

## Summary

Add backend-side filtering to the MCP registry server list endpoints. The upstream MCP registry only supports name-based search — this feature adds filtering by remote transport type, package registry type, and repository existence. When filters are applied, the service scans up to N upstream pages, filtering results in memory, and returns a single page of matching servers. This is Phase 1 (prototype); Phase 2 will replace the scanning with a local aggregator data store.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: OkHttpClient (HTTP), Jackson (JSON), Lombok, MapStruct
**Storage**: N/A (in-memory filtering; no database changes)
**Testing**: JUnit 5, Mockito, AssertJ, MockMvc; `./gradlew testFast` for dev cycle
**Target Platform**: Linux server (Spring Boot web service)
**Project Type**: Web service (REST API)
**Performance Goals**: Bounded by scan limit — max N sequential upstream HTTP calls per request
**Constraints**: Max pages to scan configurable (default 5); no changes to upstream API contract
**Scale/Scope**: Modifies 3 existing files, adds 2 new classes, adds test fixtures

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Layered architecture (web → service → dao/kubernetes) | PASS | Controller calls service; service calls client. No layer violations. |
| `@LogExecution` on all Spring components | PASS | New `McpServerFilter` component will carry `@LogExecution`. |
| MapStruct `componentModel = "spring"` | N/A | No new mappers needed. |
| `@Transactional` only on service/dao layers | N/A | No transactions involved. |
| Kubernetes isolation | N/A | No K8s changes. |
| Checkstyle (Google Java Style, 180-char lines) | PASS | Will verify with `./gradlew checkstyleMain checkstyleTest`. |
| No wildcard imports | PASS | Standard imports only. |
| Test naming: `shouldDoX()` / `shouldFailDoX_whenY()` | PASS | All new tests follow convention. |
| `docs/configuration.md` updated for new config | PASS | New env var `MCP_REGISTRY_MAX_PAGES_TO_SCAN` will be documented. |
| `CollectionUtils.isEmpty()`/`isNotEmpty()` for null-safe checks | PASS | Will use for null/empty list checks in filter. |
| `StringUtils` for string checks | PASS | Will use where applicable. |

## Project Structure

### Documentation (this feature)

```text
specs/009-mcp-registry-filtering/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output — technical decisions
├── data-model.md        # Phase 1 output — entity definitions
├── quickstart.md        # Phase 1 output — implementation guide
├── contracts/
│   └── api.md           # Phase 1 output — API contract
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/registry/mcp/
├── client/
│   └── McpRegistryClient.java              # UNCHANGED — single-page HTTP client
├── model/
│   └── (existing models)                    # UNCHANGED
├── properties/
│   └── McpRegistryProperties.java           # MODIFIED — add maxPagesToScan
├── service/
│   ├── McpRegistryService.java              # MODIFIED — multi-page scanning loop
│   └── McpServerFilter.java                 # NEW — filter predicate component
└── web/
    ├── controller/
    │   └── McpRegistryController.java       # MODIFIED — add filter query params
    └── dto/
        ├── ServersRequestDto.java           # MODIFIED — add filter field
        └── ServerFilterDto.java             # NEW — filter criteria DTO

src/main/resources/
└── application.yml                          # MODIFIED — add max-pages-to-scan

src/test/java/com/epam/aidial/deployment/manager/mcpregistry/
├── service/
│   ├── McpServerFilterTest.java             # NEW — unit tests for filter predicate
│   └── McpRegistryServiceTest.java          # NEW — unit tests for scanning loop
└── web/controller/
    └── McpRegistryControllerTest.java       # MODIFIED — add filter param tests

src/test/resources/mcp-registry/
├── servers_page.json                        # EXISTING
├── server_version.json                      # EXISTING
├── servers_page_mixed.json                  # NEW — servers with diverse properties
└── servers_page_empty.json                  # NEW — empty results page

docs/
└── configuration.md                         # MODIFIED — document new env var
```

**Structure Decision**: All changes fit within the existing `registry/mcp/` package hierarchy. Two new classes (`ServerFilterDto`, `McpServerFilter`) follow existing naming conventions. No new packages needed.

## Complexity Tracking

No constitution violations. No complexity justifications needed.
