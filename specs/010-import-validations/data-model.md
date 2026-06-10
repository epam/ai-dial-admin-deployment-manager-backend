# Data Model: Import Validations

## Existing Entities (no schema changes)

### ExportConfig (root import structure)
The deserialized config from the ZIP file. Contains maps of domain objects keyed by name:
- `mcpImageDefinitions`: Map<String, McpImageDefinition>
- `adapterImageDefinitions`: Map<String, AdapterImageDefinition>
- `interceptorImageDefinitions`: Map<String, InterceptorImageDefinition>
- `mcpDeployments`: Map<String, McpDeployment>
- `adapterDeployments`: Map<String, AdapterDeployment>
- `interceptorDeployments`: Map<String, InterceptorDeployment>
- `nimDeployments`: Map<String, NimDeployment>
- `inferenceDeployments`: Map<String, InferenceDeployment>
- `globalImageBuildDomainWhitelist`: List<String>

### Deployment (abstract, 5 subtypes)
Domain model with fields: `id`, `displayName`, `description`, `metadata`, `scaling`, `resources`, `probeProperties`, `containerPort`, `author`, `allowedDomains`, `topics`, `command`, `args`, plus subtype-specific fields.

**Validated via mapping to**: `CreateDeploymentRequestDto` (and subtypes)

### ImageDefinition (abstract, 3 subtypes)
Domain model with fields: `name`, `description`, `version`, `source`, `license`, `topics`, `author`, `allowedDomains`, `imageBuilder`, plus subtype-specific fields.

**Validated via mapping to**: `ImageDefinitionRequestDto` (and subtypes)

## New Entities

### ImportValidationError (value object)
Represents a single validation violation found during import config validation.

| Field | Type | Description |
|---|---|---|
| `entityType` | String | Component type (e.g., "MCP_DEPLOYMENT", "ADAPTER_IMAGE_DEFINITION", "GLOBAL_DOMAIN_WHITELIST") |
| `entityIdentifier` | String | Entity name/id for identification. For `GLOBAL_DOMAIN_WHITELIST` errors this is the offending domain entry itself, so each invalid domain is a separately keyed error rather than one error for the whole whitelist (empty when the offending entry is `null`). |
| `fieldPath` | String | Dot-delimited path to the invalid field (e.g., "name", "metadata.envs") |
| `message` | String | Human-readable violation description |

### ImportValidationException (exception)
Custom exception wrapping all validation errors for a failed import.

| Field | Type | Description |
|---|---|---|
| `errors` | List<ImportValidationError> | All validation violations found |

Produces a 400 BAD_REQUEST response via `DefaultExceptionHandler`.

## Mapping Relationships (new reverse mappings)

```
Deployment → CreateDeploymentRequestDto
  McpDeployment → CreateMcpDeploymentRequestDto
  AdapterDeployment → CreateAdapterDeploymentRequestDto
  InterceptorDeployment → CreateInterceptorDeploymentRequestDto
  NimDeployment → CreateNimDeploymentRequestDto
  InferenceDeployment → CreateInferenceDeploymentRequestDto

ImageDefinition → ImageDefinitionRequestDto
  McpImageDefinition → McpImageDefinitionRequestDto
  AdapterImageDefinition → AdapterImageDefinitionRequestDto
  InterceptorImageDefinition → InterceptorImageDefinitionRequestDto
```

These reverse mappings are the inverse of existing DTO→domain mappings already in `DeploymentDtoMapper` and `ImageDefinitionDtoMapper`.

## No Database Changes

This feature does not modify any database schema. No Flyway migrations required.
