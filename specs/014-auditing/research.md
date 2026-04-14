# Research: Auditing

## Decision 1: Hibernate Envers Dependency

**Decision**: Add `org.hibernate.orm:hibernate-envers` (version managed by Spring Boot BOM).

**Rationale**: The reference implementation uses Hibernate Envers directly — `@Audited`, `EntityTrackingRevisionListener`, `@RevisionEntity`, `RevisionType`. It does NOT use `spring-data-envers` (which adds `RevisionRepository` support). Since the activity layer provides the query API (not Envers revision queries), the base `hibernate-envers` module is sufficient.

**Alternatives considered**:
- `spring-data-envers`: Adds `RevisionRepository<T, ID, N>` support. Rejected because the reference implementation queries the activity table, not Envers revision tables directly. Would add unnecessary complexity.
- Custom auditing via JPA entity listeners: Rejected — reimplements what Envers already provides and is harder to maintain.

## Decision 2: UUID Generation for Activity IDs

**Decision**: Add `com.github.f4b6a3:uuid-creator` library for time-ordered UUID v7 generation.

**Rationale**: The reference uses `UuidCreator.getTimeOrderedEpoch()` for activity IDs, producing UUIDs that sort chronologically. This benefits index locality and natural ordering. Java 21 does not provide UUID v7 natively.

**Alternatives considered**:
- `UUID.randomUUID()` (v4): Random UUIDs fragment indexes over time. Rejected for performance reasons on large activity tables.
- Database-generated UUIDs: Would require different syntax per dialect. Rejected for portability.

## Decision 3: SecurityClaimsExtractor Adaptation

**Decision**: Access the existing `SecurityClaimsExtractor` Spring bean via `ApplicationContextAware` in the revision listener, same pattern as the reference.

**Rationale**: The current repo's `SecurityClaimsExtractor` is a `@Service` with instance methods. The reference repo's version uses static methods. The Envers listener is NOT a Spring bean (instantiated by Hibernate), so it cannot use constructor injection. The `ApplicationContextAware` pattern lets the listener fetch Spring beans without modifying `SecurityClaimsExtractor`.

**Author fallback for unauthenticated/system operations**: When `SecurityClaimsExtractor.getAuthor()` returns null, the listener checks `SecurityContextHolder.getContext().getAuthentication()`: if an `Authentication` object is present (unauthenticated HTTP request), the author is set to `"unknown"`; if no `Authentication` exists (system operation — K8s informer, scheduled task, build pipeline), the author is set to `"system"`.

**Alternatives considered**:
- Make `SecurityClaimsExtractor` methods static: Would work but changes the existing API, breaking all injection sites. Rejected.
- ThreadLocal for security context: Adds unnecessary complexity; `SecurityContextHolder` already works across threads.

## Decision 4: Pagination and Filtering Pattern

**Decision**: Replicate the reference repo's pattern — `POST /api/v1/activities` with a `PageRequestDto` body containing pagination, sorting, and filter criteria. The service converts these to JPA `Specification<AuditActivityEntity>` for dynamic filtering.

**Rationale**: The user explicitly requested "same patterns and endpoints." The POST-with-body approach supports complex multi-field filtering without URL length limits. JPA Specifications provide type-safe dynamic query construction.

**Alternatives considered**:
- Spring `Pageable` with `@RequestParam` filters: Simpler but limited for multi-field filtering. Rejected to match reference pattern.
- GraphQL: Over-engineered for this use case. Rejected.

## Decision 5: Transaction Timestamp Management

**Decision**: Introduce `TransactionTimestampAspect` (AOP `@Before` on `@Transactional` methods) and `TransactionTimestampContext` (reads from `TransactionSynchronizationManager` resource map), exactly mirroring the reference.

**Rationale**: Ensures all audit records within a single transaction share the exact same millisecond timestamp. Required for correlating changes that occur together. Also used by the custom `DateTimeProvider` for `@CreatedDate`/`@LastModifiedDate` fields.

**Impact**: The existing `@EnableJpaAuditing` configuration in `JpaConfiguration` will be updated to reference a `transactionDateTimeProvider` bean that reads from `TransactionTimestampContext`. This changes how `@CreatedDate`/`@LastModifiedDate` values are assigned — from "current time at flush" to "transaction start time." All timestamps within a transaction will be identical, which is the desired behavior for audit consistency.

**AOP ordering**: The aspect must fire INSIDE the transaction boundary (after `TransactionInterceptor` starts the transaction) because `bindIfAbsent()` calls `TransactionSynchronizationManager.registerSynchronization()`, which requires an active transaction. Without explicit ordering, both the aspect and `TransactionInterceptor` default to `Ordered.LOWEST_PRECEDENCE`, making execution order non-deterministic. Fix: `@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 10, proxyTargetClass = true)` on `JpaConfiguration` to give the transaction interceptor higher precedence, and `@Order(Ordered.LOWEST_PRECEDENCE)` on the aspect. `proxyTargetClass = true` is required to preserve Spring Boot's default CGLIB proxy behavior (without it, `@EnableTransactionManagement` defaults to JDK interface proxies, which breaks constructor injection of concrete service classes).

## Decision 6: Fields to Exclude from Envers Auditing

**Decision**: Audit all fields on all audited entities. Do NOT use `@NotAudited` on any fields initially.

**Rationale**: Full field auditing provides the most complete historical record. Fields like `status`, `url`, `errorMessage` on `DeploymentEntity` and `buildStatus`, `buildLogs` on `ImageDefinitionEntity` change due to system operations (K8s informers, build pipeline). These changes ARE legitimate audit events — an administrator should be able to see when a deployment went from PENDING to RUNNING. The activity layer can filter or categorize these if needed.

**Risk**: `buildLogs` (JSONB, potentially large) could increase audit table size. Monitor and add `@NotAudited` to `buildLogs` later if storage becomes a concern.

**Alternatives considered**:
- Exclude volatile fields (`status`, `buildStatus`, `buildLogs`, `url`, `errorMessage`): Reduces noise but loses legitimate audit history. Rejected initially — can be refined later.

## Decision 7: Default Activity API Filters

**Decision**: No default exclusion filters needed. The 11 concrete resource types exposed through the activity API are all user-meaningful.

**Rationale**: The reference repo excludes `RoleLimit`, `Deployment`, and `SecuredResource` because those are cascade artifacts specific to its domain model. In this repo, base types (`Deployment`, `ImageDefinition`) are excluded from `ActivityResourceType` entirely — Envers always fires `entityChanged` with the concrete subclass for JOINED inheritance, so the mapper only handles concrete types. With `DisposableResource` and `ComponentRemoval` excluded from auditing, and base types excluded from the resource type enum, all remaining resource types represent user-facing changes. No default `Specification` filters are needed.

## Decision 8: Cascading Activity Generation

**Decision**: No custom cascading logic initially. Rely on Envers' native JOINED inheritance handling.

**Rationale**: The reference repo's cascading logic (`RoleLimitEntity` → `RoleEntity` + `DeploymentEntity`) is specific to its domain model. In the current repo, the deployment and image definition hierarchies use JOINED inheritance — when a `McpDeploymentEntity` changes, Envers fires `entityChanged` with the concrete class (`McpDeploymentEntity`), and the listener maps it to `ActivityResourceType.McpDeployment`. No explicit cascade is needed because the entity hierarchy doesn't have separate "detail" entities that modify a parent.

If a similar need arises (e.g., changing `deployment_topics` should generate a `Deployment` activity), it can be added to the listener later.

## Decision 9: Envers Configuration Properties

**Decision**: Use Spring Boot's physical naming strategy defaults. Envers tables will follow lowercase conventions automatically.

**Key properties to add to `application.yml`**:
- `spring.jpa.properties.org.hibernate.envers.store_data_at_delete: true` — store full entity state on DELETE (not just the ID), enabling full reconstruction of deleted entities.

The physical naming strategy (`CamelCaseToUnderscoresNamingStrategy`) lowercases Envers' default `_AUD` suffix to `_aud` and `REV`/`REVTYPE` to `rev`/`revtype`. Flyway migrations must match these lowercase names.
