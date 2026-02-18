# Adapter Image Definitions

## Purpose
This spec describes image definitions of subtype ADAPTER (`$type: "adapter"`). Adapter image definitions add no fields beyond the base image definition; the `$type` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Adapter image definition**: An image definition of `$type: "adapter"` whose built container runs a DIAL adapter.

## Additional Fields

None. `AdapterImageDefinitionDto` is an empty subclass of `ImageDefinitionDto`. All fields are defined in the base image definition spec.

## ADDED Requirements

### Requirement: Adapter image definition has no additional fields
An adapter image definition SHALL carry only the base image definition fields. There are no adapter-specific configuration fields; `$type: "adapter"` is the sole type marker.

Status: **Implemented**

#### Scenario: Create adapter image definition
- **WHEN** `POST /api/v1/images/definitions` is called with `$type: "adapter"` and required base fields
- **THEN** an adapter image definition is created with only base fields

#### Scenario: Retrieve adapter image definition
- **WHEN** `GET /api/v1/images/definitions/{id}` returns an adapter definition
- **THEN** the response contains base fields and `$type: "adapter"` — no additional fields

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.AdapterImageDefinitionRequestDto`
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.AdapterImageDefinitionDto` (empty subclass of `ImageDefinitionDto`)
- Related specs: `image-definitions` (base CRUD and all fields), `adapter-deployments`
