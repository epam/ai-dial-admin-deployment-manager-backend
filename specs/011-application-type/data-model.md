# Data Model: Application Image Definition & Deployment Type

## Entities

### ApplicationImageDefinition

Extends `ImageDefinition` (JOINED inheritance). No additional fields.

| Attribute | Type | Source | Notes |
|-----------|------|--------|-------|
| id | UUID | Inherited from ImageDefinition | PK, auto-generated, FK to `image_definition.id` |

**Table**: `application_image_definition`

**Relationships**:
- 1:1 with `image_definition` (parent table, JOINED inheritance)
- Referenced by `ApplicationDeployment` via `deployment.image_definition_id` (when using internal image source)

**Validation**: Inherited from ImageDefinition — name, version, source required.

---

### ApplicationDeployment

Extends `Deployment` (JOINED inheritance). No additional fields.

| Attribute | Type | Source | Notes |
|-----------|------|--------|-------|
| id | VARCHAR(36) | Inherited from Deployment | PK, user-provided, FK to `deployment.id` |

**Table**: `application_deployment`

**Relationships**:
- 1:1 with `deployment` (parent table, JOINED inheritance)
- Optional reference to `image_definition` via `deployment.image_definition_id` (only when source is InternalImageSource)

**Validation**: Inherited from Deployment — name, displayName, source, metadata required.

---

## Database Migrations (V1.54)

### H2

```sql
CREATE TABLE application_image_definition (
    id UUID NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (id) REFERENCES image_definition (id) ON DELETE RESTRICT
);

CREATE TABLE application_deployment (
    id VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (id) REFERENCES deployment (id) ON DELETE RESTRICT
);
```

### PostgreSQL

```sql
CREATE TABLE application_image_definition (
    id UUID NOT NULL,
    CONSTRAINT pk_application_image_definition PRIMARY KEY (id),
    CONSTRAINT fk_application_image_definition_image_definition FOREIGN KEY (id) REFERENCES image_definition (id) ON DELETE RESTRICT
);

CREATE TABLE application_deployment (
    id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_application_deployment PRIMARY KEY (id),
    CONSTRAINT fk_application_deployment_deployment FOREIGN KEY (id) REFERENCES deployment (id) ON DELETE RESTRICT
);
```

### MS SQL Server

```sql
CREATE TABLE application_image_definition (
    id UNIQUEIDENTIFIER NOT NULL,
    CONSTRAINT pk_application_image_definition PRIMARY KEY (id),
    CONSTRAINT fk_application_image_definition_to_image_definition FOREIGN KEY (id) REFERENCES image_definition (id) ON DELETE NO ACTION
);

CREATE TABLE application_deployment (
    id VARCHAR(36) NOT NULL,
    CONSTRAINT pk_application_deployment PRIMARY KEY (id),
    CONSTRAINT fk_application_deployment_to_deployment FOREIGN KEY (id) REFERENCES deployment (id) ON DELETE NO ACTION
);
```

## State Transitions

No new state transitions. Application types inherit the standard lifecycle:

- **Image Definition**: NOT_BUILT → BUILDING → BUILD_SUCCESSFUL / BUILD_FAILED
- **Deployment**: NOT_DEPLOYED → DEPLOYING → DEPLOYED / DEPLOY_FAILED → UNDEPLOYING → NOT_DEPLOYED

## Polymorphic Type Registration

The `$type` JSON discriminator `"application"` must be registered in these parent classes:

| Parent Class | Layer | Registration |
|-------------|-------|--------------|
| `ImageDefinition` | model | `@JsonSubTypes.Type(value = ApplicationImageDefinition.class, name = "application")` |
| `Deployment` | model | `@JsonSubTypes.Type(value = ApplicationDeployment.class, name = "application")` |
| `ImageDefinitionDto` | web/dto | `@JsonSubTypes.Type(value = ApplicationImageDefinitionDto.class, name = "application")` |
| `ImageDefinitionRequestDto` | web/dto | `@JsonSubTypes.Type(value = ApplicationImageDefinitionRequestDto.class, name = "application")` |
| `DeploymentDto` | web/dto | `@JsonSubTypes.Type(value = ApplicationDeploymentDto.class, name = "application")` |
| `CreateDeploymentRequestDto` | web/dto | `@JsonSubTypes.Type(value = CreateApplicationDeploymentRequestDto.class, name = "application")` |

## Enum Additions

| Enum | New Value | Location |
|------|-----------|----------|
| `ImageType` | `APPLICATION` | `model/ImageType.java` |
| `DeploymentTypeDto` | `APPLICATION` | `web/dto/DeploymentTypeDto.java` |
