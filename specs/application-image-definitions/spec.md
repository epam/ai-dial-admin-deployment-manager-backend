# Application Image Definitions

## Purpose
This spec describes image definitions of subtype APPLICATION (`$type: "application"`). Application image definitions add no fields beyond the base image definition; the `$type` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Application image definition**: An image definition of `$type: "application"` whose built container runs a DIAL application.

## Additional Fields

None. `ApplicationImageDefinitionDto` is an empty subclass of `ImageDefinitionDto`. All fields are defined in the base image definition spec.

## Requirements

### Requirement: Application image definition has no additional fields
An application image definition SHALL carry only the base image definition fields. There are no application-specific configuration fields; `$type: "application"` is the sole type marker.

Status: **Implemented**

#### Scenario: Create application image definition
- **WHEN** `POST /api/v1/images/definitions` is called with `$type: "application"` and required base fields
- **THEN** an application image definition is created with only base fields

#### Scenario: Retrieve application image definition
- **WHEN** `GET /api/v1/images/definitions/{id}` returns an application definition
- **THEN** the response contains base fields and `$type: "application"` — no additional fields

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.ApplicationImageDefinitionRequestDto`
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.ApplicationImageDefinitionDto` (empty subclass of `ImageDefinitionDto`)
- Related specs: `image-definitions` (base CRUD and all fields), `application-deployments`
