# Database Schema

> Auto-generated from H2 Flyway migrations. Do not edit manually.
> Generated at: 2026-04-20T13:55:52.778353600Z

## Tables

- [ADAPTER_DEPLOYMENT](#adapter_deployment)
- [ADAPTER_DEPLOYMENT_AUD](#adapter_deployment_aud)
- [ADAPTER_IMAGE_DEFINITION](#adapter_image_definition)
- [ADAPTER_IMAGE_DEFINITION_AUD](#adapter_image_definition_aud)
- [APPLICATION_DEPLOYMENT](#application_deployment)
- [APPLICATION_DEPLOYMENT_AUD](#application_deployment_aud)
- [APPLICATION_IMAGE_DEFINITION](#application_image_definition)
- [APPLICATION_IMAGE_DEFINITION_AUD](#application_image_definition_aud)
- [AUDIT_ACTIVITY](#audit_activity)
- [COMPONENT_REMOVAL](#component_removal)
- [DEPLOYMENT](#deployment)
- [DEPLOYMENT_AUD](#deployment_aud)
- [DEPLOYMENT_TOPICS](#deployment_topics)
- [DEPLOYMENT_TOPICS_AUD](#deployment_topics_aud)
- [DISPOSABLE_RESOURCE](#disposable_resource)
- [DOMAIN_WHITELIST](#domain_whitelist)
- [DOMAIN_WHITELIST_AUD](#domain_whitelist_aud)
- [IMAGE_BUILD_LOGS](#image_build_logs)
- [IMAGE_DEFINITION](#image_definition)
- [IMAGE_DEFINITION_AUD](#image_definition_aud)
- [IMAGE_DEFINITION_TOPICS](#image_definition_topics)
- [IMAGE_DEFINITION_TOPICS_AUD](#image_definition_topics_aud)
- [INFERENCE_DEPLOYMENT](#inference_deployment)
- [INFERENCE_DEPLOYMENT_AUD](#inference_deployment_aud)
- [INTERCEPTOR_DEPLOYMENT](#interceptor_deployment)
- [INTERCEPTOR_DEPLOYMENT_AUD](#interceptor_deployment_aud)
- [INTERCEPTOR_IMAGE_DEFINITION](#interceptor_image_definition)
- [INTERCEPTOR_IMAGE_DEFINITION_AUD](#interceptor_image_definition_aud)
- [MCP_DEPLOYMENT](#mcp_deployment)
- [MCP_DEPLOYMENT_AUD](#mcp_deployment_aud)
- [MCP_IMAGE_DEFINITION](#mcp_image_definition)
- [MCP_IMAGE_DEFINITION_AUD](#mcp_image_definition_aud)
- [NIM_DEPLOYMENT](#nim_deployment)
- [NIM_DEPLOYMENT_AUD](#nim_deployment_aud)
- [REVINFO](#revinfo)

## ADAPTER_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |

## ADAPTER_DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |

**Indexes:**

- `FK_ADAPTER_DEPLOYMENT_AUD_REV_INDEX_4` on (REV)

## ADAPTER_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |

## ADAPTER_IMAGE_DEFINITION_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |

**Indexes:**

- `FK_ADAPTER_IMAGE_DEFINITION_AUD_REV_INDEX_3` on (REV)

## APPLICATION_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |

## APPLICATION_DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |

**Indexes:**

- `FK_APPLICATION_DEPLOYMENT_AUD_REV_INDEX_6` on (REV)

## APPLICATION_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |

## APPLICATION_IMAGE_DEFINITION_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |

**Indexes:**

- `FK_APPLICATION_IMAGE_DEFINITION_AUD_REV_INDEX_2` on (REV)

## AUDIT_ACTIVITY

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ACTIVITY_ID | VARCHAR(36) | No |  | PK |
| ACTIVITY_TYPE | VARCHAR(36) | No |  |  |
| RESOURCE_TYPE | VARCHAR(36) | Yes |  |  |
| RESOURCE_ID | VARCHAR(255) | Yes |  |  |
| EPOCH_TIMESTAMP_MS | BIGINT | Yes |  |  |
| INITIATED_AUTHOR | VARCHAR(255) | Yes |  |  |
| INITIATED_EMAIL | VARCHAR(320) | Yes |  |  |
| REVISION | INTEGER | Yes |  | FK → REVINFO.ID |

**Indexes:**

- `FK_AUDIT_ACTIVITY_REV_INDEX_4` on (REVISION)

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
| CONTAINER_PORT | INTEGER | Yes |  |  |
| ALLOWED_DOMAINS | JSON | No | JSON '[]' |  |
| PROBE_PROPERTIES | JSON | Yes |  |  |
| SCALING | JSON | Yes |  |  |
| COMMAND | JSON | Yes |  |  |
| ARGS | JSON | Yes |  |  |
| SOURCE | JSON | Yes |  |  |
| SERVICE_NAME | VARCHAR(63) | Yes |  |  |

**Indexes:**

- `UNIQUE IDX_DEPLOYMENT_SERVICE_NAME` on (SERVICE_NAME)
- `IDX_DEPLOYMENT_IMAGE_DEFINITION_ID` on (IMAGE_DEFINITION_ID)

## DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| REVTYPE | SMALLINT | Yes |  |  |
| IMAGE_DEFINITION_ID | UUID | Yes |  |  |
| SOURCE | JSON | Yes |  |  |
| DISPLAY_NAME | VARCHAR(1000000000) | Yes |  |  |
| DESCRIPTION | VARCHAR(1000000000) | Yes |  |  |
| ENVS | JSON | Yes |  |  |
| METADATA | JSON | Yes |  |  |
| SCALING | JSON | Yes |  |  |
| RESOURCES | JSON | Yes |  |  |
| PROBE_PROPERTIES | JSON | Yes |  |  |
| STATUS | VARCHAR(255) | Yes |  |  |
| URL | VARCHAR(2048) | Yes |  |  |
| CONTAINER_PORT | INTEGER | Yes |  |  |
| CREATED_AT_MS | BIGINT | Yes |  |  |
| UPDATED_AT_MS | BIGINT | Yes |  |  |
| AUTHOR | VARCHAR(255) | Yes |  |  |
| ALLOWED_DOMAINS | JSON | Yes |  |  |
| SERVICE_NAME | VARCHAR(255) | Yes |  |  |
| COMMAND | JSON | Yes |  |  |
| ARGS | JSON | Yes |  |  |

**Indexes:**

- `FK_DEPLOYMENT_AUD_REV_INDEX_D` on (REV)

## DEPLOYMENT_TOPICS

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| DEPLOYMENT_ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| TOPIC_NAME | VARCHAR(255) | No |  | PK |

**Indexes:**

- `FK_DEPLOYMENT_TOPICS_DEPLOYMENT_INDEX_B` on (DEPLOYMENT_ID)

## DEPLOYMENT_TOPICS_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| REVTYPE | SMALLINT | Yes |  |  |
| DEPLOYMENT_ID | VARCHAR(36) | No |  | PK |
| TOPIC_NAME | VARCHAR(255) | No |  | PK |

**Indexes:**

- `FK_DEPLOYMENT_TOPICS_AUD_REV_INDEX_F` on (REV)

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

## DOMAIN_WHITELIST_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| REVTYPE | SMALLINT | Yes |  |  |
| ALLOWED_DOMAINS | JSON | Yes |  |  |
| UPDATED_AT | TIMESTAMP | Yes |  |  |

**Indexes:**

- `FK_DOMAIN_WHITELIST_AUD_REV_INDEX_6` on (REV)

## IMAGE_BUILD_LOGS

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| IMAGE_DEFINITION_ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |
| LOGS | JSON | Yes |  |  |

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
| BUILT_AT_MS | BIGINT | Yes |  |  |
| TYPE | VARCHAR(20) | No |  |  |
| ALLOWED_DOMAINS | JSON | No | JSON '[]' |  |
| IMAGE_BUILDER | VARCHAR(32) | No | 'BUILDKIT_ROOTLESS' |  |

**Indexes:**

- `UNIQUE UQ_IMAGE_DEFINITION_TYPE_NAME_VERSION_INDEX_8` on (TYPE, NAME, VERSION)
- `IDX_IMAGE_DEFINITION_NAME` on (NAME)

## IMAGE_DEFINITION_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| REVTYPE | SMALLINT | Yes |  |  |
| NAME | VARCHAR(255) | Yes |  |  |
| DESCRIPTION | VARCHAR(1000000000) | Yes |  |  |
| VERSION | VARCHAR(255) | Yes |  |  |
| TYPE | VARCHAR(255) | Yes |  |  |
| SOURCE | JSON | Yes |  |  |
| LICENSE | VARCHAR(255) | Yes |  |  |
| CREATED_AT_MS | BIGINT | Yes |  |  |
| UPDATED_AT_MS | BIGINT | Yes |  |  |
| IMAGE_NAME | VARCHAR(255) | Yes |  |  |
| BUILD_STATUS | VARCHAR(255) | Yes |  |  |
| BUILT_AT_MS | BIGINT | Yes |  |  |
| AUTHOR | VARCHAR(255) | Yes |  |  |
| ALLOWED_DOMAINS | JSON | Yes |  |  |
| IMAGE_BUILDER | VARCHAR(255) | Yes |  |  |

**Indexes:**

- `FK_IMAGE_DEFINITION_AUD_REV_INDEX_9` on (REV)

## IMAGE_DEFINITION_TOPICS

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| IMAGE_DEFINITION_ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |
| TOPIC_NAME | VARCHAR(255) | No |  | PK |

**Indexes:**

- `FK_IMAGE_DEFINITION_TOPICS_IMAGE_DEFINITION_INDEX_A` on (IMAGE_DEFINITION_ID)

## IMAGE_DEFINITION_TOPICS_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| REVTYPE | SMALLINT | Yes |  |  |
| IMAGE_DEFINITION_ID | UUID | No |  | PK |
| TOPIC_NAME | VARCHAR(255) | No |  | PK |

**Indexes:**

- `FK_IMAGE_DEFINITION_TOPICS_AUD_REV_INDEX_4` on (REV)

## INFERENCE_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| MODEL_FORMAT | VARCHAR(32) | No |  |  |

## INFERENCE_DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| MODEL_FORMAT | VARCHAR(255) | Yes |  |  |

**Indexes:**

- `FK_INFERENCE_DEPLOYMENT_AUD_REV_INDEX_4` on (REV)

## INTERCEPTOR_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |

## INTERCEPTOR_DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |

**Indexes:**

- `FK_INTERCEPTOR_DEPLOYMENT_AUD_REV_INDEX_6` on (REV)

## INTERCEPTOR_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |

## INTERCEPTOR_IMAGE_DEFINITION_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |

**Indexes:**

- `FK_INTERCEPTOR_IMAGE_DEFINITION_AUD_REV_INDEX_7` on (REV)

## MCP_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| TRANSPORT | VARCHAR(64) | Yes |  |  |
| MCP_ENDPOINT_PATH | VARCHAR(1000000000) | Yes |  |  |

## MCP_DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| TRANSPORT | VARCHAR(255) | Yes |  |  |
| MCP_ENDPOINT_PATH | VARCHAR(1000000000) | Yes |  |  |

**Indexes:**

- `FK_MCP_DEPLOYMENT_AUD_REV_INDEX_E` on (REV)

## MCP_IMAGE_DEFINITION

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK, FK → IMAGE_DEFINITION.ID |
| TRANSPORT_TYPE | VARCHAR(32) | Yes |  |  |

## MCP_IMAGE_DEFINITION_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | UUID | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| TRANSPORT_TYPE | VARCHAR(255) | Yes |  |  |

**Indexes:**

- `FK_MCP_IMAGE_DEFINITION_AUD_REV_INDEX_8` on (REV)

## NIM_DEPLOYMENT

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK, FK → DEPLOYMENT.ID |
| CONTAINER_GRPC_PORT | INTEGER | Yes |  |  |
| STORAGE_SIZE | VARCHAR(64) | Yes |  |  |

## NIM_DEPLOYMENT_AUD

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | VARCHAR(36) | No |  | PK |
| REV | INTEGER | No |  | PK, FK → REVINFO.ID |
| CONTAINER_GRPC_PORT | INTEGER | Yes |  |  |
| STORAGE_SIZE | VARCHAR(64) | Yes |  |  |

**Indexes:**

- `FK_NIM_DEPLOYMENT_AUD_REV_INDEX_9` on (REV)

## REVINFO

| Column | Type | Nullable | Default | Key |
|--------|------|----------|---------|-----|
| ID | INTEGER | No |  | PK |
| TIMESTAMP | BIGINT | No |  |  |
| AUTHOR | VARCHAR(255) | Yes |  |  |
| EMAIL | VARCHAR(320) | Yes |  |  |

**Indexes:**

- `IDX_REVINFO_TIMESTAMP_ID` on (TIMESTAMP, ID)

