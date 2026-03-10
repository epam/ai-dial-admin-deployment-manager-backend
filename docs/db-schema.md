# Database Schema

> Auto-generated from H2 Flyway migrations. Do not edit manually.
> Generated at: 2026-03-10T12:42:25.679847200Z

## Tables

- [ADAPTER_DEPLOYMENT](#adapter_deployment)
- [ADAPTER_IMAGE_DEFINITION](#adapter_image_definition)
- [COMPONENT_REMOVAL](#component_removal)
- [DEPLOYMENT](#deployment)
- [DEPLOYMENT_TOPICS](#deployment_topics)
- [DISPOSABLE_RESOURCE](#disposable_resource)
- [DOMAIN_WHITELIST](#domain_whitelist)
- [IMAGE_DEFINITION](#image_definition)
- [IMAGE_DEFINITION_TOPICS](#image_definition_topics)
- [INFERENCE_DEPLOYMENT](#inference_deployment)
- [INTERCEPTOR_DEPLOYMENT](#interceptor_deployment)
- [INTERCEPTOR_IMAGE_DEFINITION](#interceptor_image_definition)
- [MCP_DEPLOYMENT](#mcp_deployment)
- [MCP_IMAGE_DEFINITION](#mcp_image_definition)
- [NIM_DEPLOYMENT](#nim_deployment)

## ADAPTER_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |

## ADAPTER_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |

## COMPONENT_REMOVAL

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| TYPE | VARCHAR(32) | No |  | PK |
| CREATED_AT | TIMESTAMP | No |  |  |

## DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| IMAGE_DEFINITION_ID | UUID | Yes |  | FK → IMAGE_DEFINITION.ID |
| DISPLAY_NAME | VARCHAR(1000000000) | No |  |  |
| DESCRIPTION | VARCHAR(1000000000) | Yes |  |  |
| ENVS | JSON | Yes |  |  |
| STATUS | VARCHAR(32) | Yes |  |  |
| URL | VARCHAR(2048) | Yes |  |  |
| RESOURCES | JSON | Yes |  |  |
| CREATED_AT_MS | BIGINT | No |  |  |
| UPDATED_AT_MS | BIGINT | No |  |  |
| AUTHOR | VARCHAR(1000000000) | Yes |  |  |
| METADATA | JSON | Yes |  |  |
| IMAGE_DEFINITION_NAME | VARCHAR(255) | Yes |  |  |
| IMAGE_DEFINITION_VERSION | VARCHAR(255) | Yes |  |  |
| CONTAINER_PORT | INTEGER | Yes |  |  |
| ALLOWED_DOMAINS | JSON | No | JSON '[]' |  |
| PROBE_PROPERTIES | JSON | Yes |  |  |
| IMAGE_DEFINITION_TYPE | VARCHAR(20) | Yes |  |  |
| SCALING | JSON | Yes |  |  |
| COMMAND | JSON | Yes |  |  |
| ARGS | JSON | Yes |  |  |

**Indexes:**

- `IDX_DEPLOYMENT_IMAGE_DEFINITION_ID` on (IMAGE_DEFINITION_ID)

## DEPLOYMENT_TOPICS

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| DEPLOYMENT_ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| TOPIC_NAME | VARCHAR(255) | No |  | PK |

**Indexes:**

- `FK_DEPLOYMENT_TOPICS_DEPLOYMENT_INDEX_B` on (DEPLOYMENT_ID)

## DISPOSABLE_RESOURCE

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No | RANDOM_UUID() | PK |
| GROUP_ID | VARCHAR(36) | No |  |  |
| RESOURCE_REFERENCE | JSON | No |  |  |
| LIFECYCLE_STATE | VARCHAR(32) | No |  |  |
| CREATED_AT | TIMESTAMP | No |  |  |

**Indexes:**

- `IDX_DISPOSABLE_RESOURCE_GROUP_ID` on (GROUP_ID)

## DOMAIN_WHITELIST

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| ALLOWED_DOMAINS | JSON | No |  |  |
| UPDATED_AT | TIMESTAMP | No |  |  |

## IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No | RANDOM_UUID() | PK |
| NAME | VARCHAR(255) | No |  |  |
| DESCRIPTION | VARCHAR(1000000000) | Yes |  |  |
| SOURCE | JSON | No |  |  |
| LICENSE | VARCHAR(255) | Yes |  |  |
| CREATED_AT_MS | BIGINT | No |  |  |
| UPDATED_AT_MS | BIGINT | No |  |  |
| AUTHOR | VARCHAR(1000000000) | Yes |  |  |
| VERSION | VARCHAR(255) | No | '1.0.0' |  |
| IMAGE_NAME | VARCHAR(255) | Yes |  |  |
| BUILD_STATUS | VARCHAR(32) | Yes |  |  |
| BUILD_LOGS | JSON | Yes |  |  |
| BUILT_AT_MS | BIGINT | Yes |  |  |
| TYPE | VARCHAR(20) | No |  |  |
| ALLOWED_DOMAINS | JSON | No | JSON '[]' |  |
| IMAGE_BUILDER | VARCHAR(32) | No | 'BUILDKIT_ROOTLESS' |  |

**Indexes:**

- `UNIQUE UQ_IMAGE_DEFINITION_TYPE_NAME_VERSION_INDEX_8` on (TYPE, NAME, VERSION)
- `IDX_IMAGE_DEFINITION_NAME` on (NAME)

## IMAGE_DEFINITION_TOPICS

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| IMAGE_DEFINITION_ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |
| TOPIC_NAME | VARCHAR(255) | No |  | PK |

**Indexes:**

- `FK_IMAGE_DEFINITION_TOPICS_IMAGE_DEFINITION_INDEX_A` on (IMAGE_DEFINITION_ID)

## INFERENCE_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| MODEL_FORMAT | VARCHAR(32) | No |  |  |
| SOURCE | JSON | No |  |  |

## INTERCEPTOR_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |

## INTERCEPTOR_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |

## MCP_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| TRANSPORT | VARCHAR(64) | Yes |  |  |
| MCP_ENDPOINT_PATH | VARCHAR(1000000000) | Yes |  |  |

## MCP_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |
| TRANSPORT_TYPE | VARCHAR(32) | Yes |  |  |

## NIM_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| CONTAINER_GRPC_PORT | INTEGER | Yes |  |  |
| SOURCE | JSON | No |  |  |

