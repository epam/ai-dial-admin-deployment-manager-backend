# Implementation Plan: Config Export Preview

**Branch**: `006-config-export-preview` | **Date**: 2026-03-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/006-config-export-preview/spec.md`

## Summary

Add a read-only `POST /api/v1/configs/export-preview` endpoint that accepts the same `ExportRequestDto`
as the existing export endpoint and returns an `ExportConfigPreviewDto` containing two lists of
`ExportComponentInfoDto` (one for image definitions, one for deployments) and the global domain
whitelist. The implementation reuses `ConfigExporter.getConfig()` for entity resolution and
adds a concrete mapping method to `ExportConfigMapper` for converting the `ExportConfig` domain
model to the preview DTOs.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.5.10, MapStruct 1.6.0, Lombok, SpringDoc OpenAPI 2.8.5
**Storage**: N/A (read-only, no DB writes)
**Testing**: `./gradlew testFast` (H2 functional tests)
**Target Platform**: Spring Boot web service
**Project Type**: Web service
**Performance Goals**: Preview response in < 3 s for up to 100 entities (SC-001)
**Constraints**: Read-only — no writes, no streaming, no ZIP
**Scale/Scope**: Same entity scope as the existing export endpoint

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Rule | Status | Notes |
|---|---|---|
| Strict layered architecture (web → service → dao) | PASS | Controller calls service for `ExportConfig` (domain model), then uses web-layer mapper to produce DTO — no layer shortcuts |
| No `@Transactional` on controllers | PASS | Transaction lives in the new `ConfigTransferService.getExportConfig()` |
| `@LogExecution` on all Spring components | PASS | No new `@Component`/`@Service` added; all touched classes already annotated |
| `*Dto` / `*RequestDto` naming | PASS | `ExportConfigPreviewDto`, `ExportComponentInfoDto` follow convention |
| Web mappers in `web/mapper/` | PASS | `ExportConfigMapper` stays in `web/mapper/`; converting interface → abstract class follows established pattern (`DeploymentDtoMapper`) |
| No Kubernetes API calls outside `kubernetes/` | PASS | Feature is purely read, no K8s involvement |
| Checkstyle / `-Werror` | PASS | Uses `Stream.of(...).flatMap()` and switch/instanceof patterns already present in codebase |
| No new DB migrations | PASS | Feature is read-only |
| No new config properties / `docs/configuration.md` update | PASS | No new env vars or `@ConfigurationProperties` fields |

## Project Structure

### Documentation (this feature)

```text
specs/006-config-export-preview/
├── plan.md              # This file
├── research.md          # Phase 0 (no unknowns — skipped directly to Phase 1)
├── data-model.md        # Phase 1 — new DTOs
├── contracts/           # Phase 1 — API contract
└── tasks.md             # Phase 2 (/speckit.tasks — NOT created here)
```

### Source Code Changes

```text
src/main/java/com/epam/aidial/deployment/manager/
├── web/
│   ├── controller/
│   │   └── ConfigController.java                      [CHANGED] add previewConfig endpoint
│   ├── dto/
│   │   └── config/
│   │       ├── ExportConfigPreviewDto.java             [NEW] record
│   │       └── ExportComponentInfoDto.java             [NEW] record
│   └── mapper/
│       └── ExportConfigMapper.java                    [CHANGED] interface → abstract class;
│                                                               add toExportConfigPreviewDto()
└── service/
    └── config/
        └── ConfigTransferService.java                 [CHANGED] add getExportConfig()

src/test/java/com/epam/aidial/deployment/manager/
└── functional/
    └── tests/
        └── ConfigExportImportFunctionalTest.java      [CHANGED] add preview test cases
```

## Phase 0: Research

No unknowns. All required patterns are established in the codebase.

## Phase 1: Design & Contracts

See [data-model.md](data-model.md) and [contracts/export-preview.md](contracts/export-preview.md).

### Key Design Decisions

#### 1. Controller owns the ExportConfig → DTO conversion

`ConfigTransferService` adds a `@Transactional(readOnly = true) ExportConfig getExportConfig(ExportRequest)`
method that returns the resolved domain model. The controller calls it, then delegates to
`exportConfigMapper.toExportConfigPreviewDto(config)` for the DTO conversion. This keeps the
service layer free of web-layer DTO types — consistent with how the existing export flow works
(`exportConfig()` returns `StreamingResponseBody`, a Spring type, while the DTO mapping happens
in the controller via `exportConfigMapper.toExportRequest(dto)`).

#### 2. ExportConfigMapper: interface → abstract class

The conversion from `ExportConfig` to `ExportConfigPreviewDto` requires iterating five deployment
maps and three image-definition maps (all `LinkedHashMap<String, *>` typed by subtype), flattening
them into two flat lists, and calling private helper methods for the field mapping. This logic
cannot be expressed with MapStruct annotations alone and must be a concrete Java method.

Converting `ExportConfigMapper` from interface to abstract class is the established pattern in
this codebase (`DeploymentDtoMapper` is an abstract class for the same reason). The existing
`toExportRequest` method becomes `public abstract`.

No `uses` additions are needed — the new helpers only reference domain model fields directly.

```java
@Mapper(componentModel = "spring")
public abstract class ExportConfigMapper {

    @SubclassMapping(source = SelectedItemsExportRequestDto.class, target = SelectedItemsExportRequest.class)
    @BeanMapping(subclassExhaustiveStrategy = RUNTIME_EXCEPTION)
    public abstract ExportRequest toExportRequest(ExportRequestDto dto);

    public ExportConfigPreviewDto toExportConfigPreviewDto(ExportConfig config) {
        var imageDefinitions = Stream.of(
                config.getMcpImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, MCP_IMAGE_DEFINITION)),
                config.getAdapterImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, ADAPTER_IMAGE_DEFINITION)),
                config.getInterceptorImageDefinitions().values().stream()
                        .map(d -> toComponentInfoDto(d, INTERCEPTOR_IMAGE_DEFINITION))
        ).flatMap(s -> s).toList();

        var deployments = Stream.of(
                config.getMcpDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, MCP_DEPLOYMENT)),
                config.getAdapterDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, ADAPTER_DEPLOYMENT)),
                config.getInterceptorDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, INTERCEPTOR_DEPLOYMENT)),
                config.getNimDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, NIM_DEPLOYMENT)),
                config.getInferenceDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, INFERENCE_DEPLOYMENT))
        ).flatMap(s -> s).toList();

        return new ExportConfigPreviewDto(
                config.getGlobalImageBuildDomainWhitelist(),
                imageDefinitions,
                deployments
        );
    }

    private ExportComponentInfoDto toComponentInfoDto(ImageDefinition imageDef,
                                                      ExportConfigComponentTypeDto type) {
        return new ExportComponentInfoDto(
                imageDef.getId().toString(),   // UUID → String
                imageDef.getName(),            // name → displayName
                imageDef.getVersion(),
                imageDef.getDescription(),
                type
        );
    }

    private ExportComponentInfoDto toComponentInfoDto(Deployment deployment,
                                                      ExportConfigComponentTypeDto type) {
        return new ExportComponentInfoDto(
                deployment.getId(),
                deployment.getDisplayName(),
                null,                          // version is empty for deployments
                deployment.getDescription(),
                type
        );
    }
}
```

#### 3. ExportComponentInfoDto and ExportConfigPreviewDto as Java records

Both DTOs are simple, immutable, response-only, with no inheritance — Java `record` is the
correct form per the constitution.

#### 4. `addSecrets` has no effect on preview

Confirmed during spec clarification. `ExportComponentInfoDto` carries no env-var fields. No
special handling needed; the flag is accepted in the request for API consistency.

## Complexity Tracking

No constitution violations. No entry needed.
