# Data Model: Config Export Preview

**Branch**: `006-config-export-preview` | **Date**: 2026-03-16

## New DTO: `ExportComponentInfoDto`

**Package**: `com.epam.aidial.deployment.manager.web.dto.config`
**Type**: Java `record`

```java
public record ExportComponentInfoDto(
        String id,
        String displayName,
        String version,
        String description,
        ExportConfigComponentTypeDto type
) {}
```

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | `String` | No | For image definitions: UUID stringified. For deployments: the deployment ID string. |
| `displayName` | `String` | Yes | For image definitions: the `name` field. For deployments: the `displayName` field. |
| `version` | `String` | Yes | For image definitions: the `version` field. For deployments: `null` (empty). |
| `description` | `String` | Yes | Description field from both entity types. |
| `type` | `ExportConfigComponentTypeDto` | No | Enum value identifying the entity type. |

### Field Mapping

| Source entity | Source field | `ExportComponentInfoDto` field |
|---|---|---|
| `ImageDefinition` | `id` (UUID) | `id` (`.toString()`) |
| `ImageDefinition` | `name` | `displayName` |
| `ImageDefinition` | `version` | `version` |
| `ImageDefinition` | `description` | `description` |
| `McpImageDefinition` | — | `type = MCP_IMAGE_DEFINITION` |
| `AdapterImageDefinition` | — | `type = ADAPTER_IMAGE_DEFINITION` |
| `InterceptorImageDefinition` | — | `type = INTERCEPTOR_IMAGE_DEFINITION` |
| `Deployment` | `id` (String) | `id` |
| `Deployment` | `displayName` | `displayName` |
| — | — | `version = null` |
| `Deployment` | `description` | `description` |
| `McpDeployment` | — | `type = MCP_DEPLOYMENT` |
| `AdapterDeployment` | — | `type = ADAPTER_DEPLOYMENT` |
| `InterceptorDeployment` | — | `type = INTERCEPTOR_DEPLOYMENT` |
| `NimDeployment` | — | `type = NIM_DEPLOYMENT` |
| `InferenceDeployment` | — | `type = INFERENCE_DEPLOYMENT` |

---

## New DTO: `ExportConfigPreviewDto`

**Package**: `com.epam.aidial.deployment.manager.web.dto.config`
**Type**: Java `record`

```java
public record ExportConfigPreviewDto(
        List<String> globalImageBuildDomainWhitelist,
        List<ExportComponentInfoDto> imageDefinitions,
        List<ExportComponentInfoDto> deployments
) {}
```

| Field | Type | Nullable | Description |
|---|---|---|---|
| `globalImageBuildDomainWhitelist` | `List<String>` | No (empty list) | Global image build domain whitelist. Empty when `addGlobalImageBuildDomainWhitelist` was `false`. |
| `imageDefinitions` | `List<ExportComponentInfoDto>` | No (empty list) | Resolved image definitions — MCP, then Adapter, then Interceptor (map insertion order). |
| `deployments` | `List<ExportComponentInfoDto>` | No (empty list) | Resolved deployments — MCP, Adapter, Interceptor, NIM, Inference (map insertion order). |

### Mapping source

`ExportConfigPreviewDto` is assembled from `ExportConfig` by `ExportConfigMapper.toExportConfigPreviewDto(ExportConfig)`:

| `ExportConfig` field | → `ExportConfigPreviewDto` field |
|---|---|
| `globalImageBuildDomainWhitelist` | `globalImageBuildDomainWhitelist` (copied directly) |
| `mcpImageDefinitions.values()` + `adapterImageDefinitions.values()` + `interceptorImageDefinitions.values()` | `imageDefinitions` (flattened, each mapped to `ExportComponentInfoDto`) |
| `mcpDeployments.values()` + `adapterDeployments.values()` + `interceptorDeployments.values()` + `nimDeployments.values()` + `inferenceDeployments.values()` | `deployments` (flattened, each mapped to `ExportComponentInfoDto`) |
