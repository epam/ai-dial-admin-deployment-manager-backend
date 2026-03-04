# Interceptor Image Definitions

## Purpose
This spec describes image definitions of subtype INTERCEPTOR (`$type: "interceptor"`). Interceptor image definitions add no fields beyond the base image definition; the `$type` discriminator is the sole distinction.

Status: **Implemented**

## Key Terms
- **Interceptor image definition**: An image definition of `$type: "interceptor"` whose built container runs a DIAL interceptor.

## Additional Fields

None. `InterceptorImageDefinitionDto` is an empty subclass of `ImageDefinitionDto`. All fields are defined in the base image definition spec.

## Requirements

### Requirement: Interceptor image definition has no additional fields
An interceptor image definition SHALL carry only the base image definition fields. There are no interceptor-specific configuration fields; `$type: "interceptor"` is the sole type marker.

Status: **Implemented**

#### Scenario: Create interceptor image definition
- **WHEN** `POST /api/v1/images/definitions` is called with `$type: "interceptor"` and required base fields
- **THEN** an interceptor image definition is created with only base fields

#### Scenario: Retrieve interceptor image definition
- **WHEN** `GET /api/v1/images/definitions/{id}` returns an interceptor definition
- **THEN** the response contains base fields and `$type: "interceptor"` — no additional fields

## Implementation Notes
- Request DTO: `com.epam.aidial.deployment.manager.web.dto.InterceptorImageDefinitionRequestDto`
- Response DTO: `com.epam.aidial.deployment.manager.web.dto.InterceptorImageDefinitionDto` (empty subclass of `ImageDefinitionDto`)
- Related specs: `image-definitions` (base CRUD and all fields), `interceptor-deployments`
