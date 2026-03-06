# Implementation Plan: Deployment Topics

**Branch**: `001-deployment-topics` | **Date**: 2026-03-05 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-deployment-topics/spec.md`

## Summary

Add an optional `topics` field (list of free-form string labels) to deployments, mirroring the existing implementation on image definitions. This involves: a new `deployment_topics` join table, model/DTO/entity additions across all layers, validation reuse (`@ValidTopics`), updating the global topics listing query, and ensuring duplication + export/import propagate topics.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Hibernate/JPA (via Spring Data), MapStruct 1.6.0, Flyway 11.14.0, Lombok, Jackson 2.21.1
**Storage**: PostgreSQL, SQL Server, H2 (multi-vendor via Flyway)
**Testing**: JUnit 5, Testcontainers 1.21.3, AssertJ, H2/Postgres/SQL Server functional tests
**Target Platform**: Linux server (K8s)
**Project Type**: Web service (Spring Boot REST API)
**Performance Goals**: N/A (additive field; no new latency-sensitive paths)
**Constraints**: Checkstyle 10.21.4 (Google Java Style, 180 char line limit, `-Werror`)
**Scale/Scope**: Small feature — ~15 files modified, 3 new migration files, 0 new Java classes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Strict Layered Architecture | PASS | Topics field added at each layer following existing pattern |
| II. Transactional Discipline | PASS | No new `@Transactional` — existing service methods handle persistence |
| III. Kubernetes Isolation | PASS | No K8s API involvement |
| IV. Observability First | PASS | No new components; existing `@LogExecution` components unchanged |
| V. Security by Configuration | PASS | Topics are not sensitive data |
| Code Style | PASS | All changes follow Google Java Style |
| Multi-Vendor Database | PASS | Migration in all 3 vendor directories |
| Anti-Patterns | PASS | No anti-patterns introduced |

## Project Structure

### Documentation (this feature)

```text
specs/001-deployment-topics/
├── spec.md
├── plan.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (files to modify)

```text
src/main/
├── java/com/epam/aidial/deployment/manager/
│   ├── model/deployment/
│   │   ├── Deployment.java                    # Add topics field
│   │   └── CreateDeployment.java              # Add topics field
│   ├── dao/
│   │   ├── entity/deployment/
│   │   │   └── DeploymentEntity.java          # Add @ElementCollection topics
│   │   ├── mapper/
│   │   │   └── PersistenceDeploymentMapper.java # Add topics to updateEntityFromDomain
│   │   └── repository/
│   │       └── TopicRepository.java           # Extend query to UNION deployment topics
│   └── web/dto/
│       ├── deployment/
│       │   ├── CreateDeploymentRequestDto.java # Add @ValidTopics topics field
│       │   └── DeploymentDto.java             # Add topics field
│       └── DeploymentInfoDto.java             # Add topics field
└── resources/db/migration/
    ├── H2/V1.47__CreateDeploymentTopicsTable.sql
    ├── POSTGRES/V1.47__CreateDeploymentTopicsTable.sql
    └── MS_SQL_SERVER/V1.47__CreateDeploymentTopicsTable.sql
```

## Design Decisions

### D1: Reuse `@ValidTopics` annotation

The existing `@ValidTopics` + `TopicsValidator` validates: non-blank, ≤255 chars, no leading/trailing whitespace. Apply the same annotation to `CreateDeploymentRequestDto.topics`. No new validation code needed.

### D2: `deployment_topics` join table (separate from `image_definition_topics`)

As discussed — two separate tables preserve FK integrity, cascade delete, and type-safe references. The `deployment` table uses `VARCHAR` ID vs `UUID` on `image_definition`, making a polymorphic table impractical.

### D3: TopicRepository query — UNION approach

Current query:
```sql
select distinct t from ImageDefinitionEntity i join i.topics t order by t
```

Updated to native SQL UNION:
```sql
SELECT DISTINCT topic_name FROM (
    SELECT topic_name FROM image_definition_topics
    UNION
    SELECT topic_name FROM deployment_topics
) AS all_topics ORDER BY topic_name
```

This is simpler and more efficient than two JPQL queries merged in Java. Native query is acceptable here since it's a simple union of two identical-schema tables.

### D4: MapStruct auto-mapping for topics

Since the field name `topics` and type `List<String>` are identical across all layers (DTO → domain → entity), MapStruct will auto-map without explicit `@Mapping` annotations. Only `PersistenceDeploymentMapper.updateEntityFromDomain()` needs a manual line added (it's a hand-written method).

### D5: Export/Import — no mix-in changes needed

`DeploymentExportMixIn` only excludes specific fields (url, status, author, timestamps, imageDefinitionId). Since `topics` is not excluded, it will automatically be included in exports. The import path deserializes via the `Deployment` domain model, which will include topics once the field is added.

### D6: Duplication — automatic via mapper chain

`DeploymentMapper.toCreateCloneDeployment()` calls `toCreateDeployment(deployment)` which is a MapStruct-generated method. Once `topics` exists on both `Deployment` and `CreateDeployment`, the field will be copied automatically.

## Change Inventory

### Layer 1: Database Migration (V1.47)

**H2 and POSTGRES** — `src/main/resources/db/migration/{H2,POSTGRES}/V1.47__CreateDeploymentTopicsTable.sql`:

```sql
CREATE TABLE deployment_topics (
    deployment_id VARCHAR(36)  NOT NULL,
    topic_name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_deployment_topics PRIMARY KEY (deployment_id, topic_name),
    CONSTRAINT fk_deployment_topics_deployment FOREIGN KEY (deployment_id)
        REFERENCES deployment (id) ON DELETE CASCADE
);
```

**MS_SQL_SERVER** — `src/main/resources/db/migration/MS_SQL_SERVER/V1.47__CreateDeploymentTopicsTable.sql`:

```sql
CREATE TABLE deployment_topics (
    deployment_id VARCHAR(36)  NOT NULL,
    topic_name    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_deployment_topics PRIMARY KEY (deployment_id, topic_name),
    CONSTRAINT fk_deployment_topics_deployment FOREIGN KEY (deployment_id)
        REFERENCES deployment (id) ON DELETE CASCADE
);
```

Note: `deployment.id` is `VARCHAR(36)` across all vendors (changed from UUID in V1.42). The DDL is identical for this table since it only uses `VARCHAR` columns (no JSON, NVARCHAR, or UUID types that differ across vendors).

### Layer 2: Entity

**File**: `DeploymentEntity.java` — add after `allowedDomains`:

```java
@ElementCollection
@CollectionTable(name = "deployment_topics", joinColumns = @JoinColumn(name = "deployment_id"))
@Column(name = "topic_name")
private List<String> topics;
```

### Layer 3: Domain Models

**File**: `Deployment.java` — add field:
```java
private List<String> topics;
```

**File**: `CreateDeployment.java` — add field:
```java
private List<String> topics;
```

### Layer 4: DTOs

**File**: `CreateDeploymentRequestDto.java` — add after `allowedDomains`:
```java
@Nullable
@ValidTopics
private List<String> topics;
```

**File**: `DeploymentDto.java` — add after `allowedDomains`:
```java
@NotNull
private List<String> allowedDomains;  // existing
@Nullable
private List<String> topics;
```

**File**: `DeploymentInfoDto.java` — add to record parameters:
```java
@Nullable List<String> topics
```

### Layer 5: Mappers

**File**: `PersistenceDeploymentMapper.java` — add line in `updateEntityFromDomain()`:
```java
existingEntity.setTopics(updatedEntity.getTopics());
```

All other mappers (DeploymentDtoMapper, DeploymentMapper) will auto-map `topics` via MapStruct.

### Layer 6: TopicRepository

**File**: `TopicRepository.java` — replace JPQL with native UNION query:

```java
public List<String> getAllTopics() {
    return entityManager.createNativeQuery("""
            SELECT DISTINCT topic_name FROM (
                SELECT topic_name FROM image_definition_topics
                UNION
                SELECT topic_name FROM deployment_topics
            ) AS all_topics ORDER BY topic_name
            """, String.class)
            .getResultList();
}
```

### Layer 7: Tests

Update functional tests to cover:
1. Create deployment with topics → topics persisted and returned
2. Create deployment without topics → empty list returned
3. Update deployment topics → topics replaced
4. Delete deployment → topics cascade-deleted
5. Topics listing includes deployment-only topics
6. Topics listing deduplicates across image definitions and deployments
7. Duplicate deployment → topics copied
8. Invalid topics rejected (blank, >255 chars, whitespace-padded)

Existing topic functional tests in `TopicFunctionalTest.java` need extension. Existing deployment tests should continue passing (backward-compatible).
