# Implementation Plan: Auditing

**Branch**: `014-auditing` | **Date**: 2026-04-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/014-auditing/spec.md`

## Summary

Add a two-tier auditing system to the Deployment Manager backend, replicating the proven architecture from `ai-dial-admin-backend`. Tier 1 uses Hibernate Envers to automatically snapshot full entity state on every change into `*_aud` tables. Tier 2 uses a custom `EntityTrackingRevisionListener` to build a curated, denormalized activity feed (`audit_activity`) capturing WHO, WHEN, WHAT, and HOW for each change — exposed via a filterable REST API at `POST /api/v1/activities` and `GET /api/v1/activities/{activityId}`.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Hibernate Envers (new), uuid-creator (new), Spring Data JPA (existing), Spring Security (existing), Flyway 11.14.0 (existing), MapStruct 1.6.0 (existing)
**Storage**: H2 (dev/test), PostgreSQL 42.7.8, SQL Server 13.2.1 — all via Flyway migrations
**Testing**: JUnit 5 + AssertJ, Testcontainers 1.21.3 (Postgres/SQL Server), H2 for `testFast`
**Target Platform**: Linux server (Spring Boot JAR / Docker)
**Project Type**: Web service (REST API)
**Performance Goals**: Activity list query < 2 seconds under normal load
**Constraints**: `ddl-auto: validate` — Flyway owns schema; 180-char line limit; `-Werror`
**Scale/Scope**: 13 audited entities (11 concrete resource types), 16 new database tables, ~20 new Java classes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| **Strict Layered Architecture** | PASS | New code follows `web → service → dao` strictly. Controller calls service; service calls repository; audit entities in `dao.audit`. |
| **Transactional Discipline** | PASS | `@Transactional` only on `AuditActivityService` (service) and dao layer. Controller has no `@Transactional`. |
| **Kubernetes Isolation** | PASS | No K8s API calls in auditing code. |
| **Observability First** | PASS | `@LogExecution` on all new Spring components. |
| **Security by Configuration** | PASS | Activity endpoints inherit existing security filter chain. Author/email from existing `SecurityClaimsExtractor`. |
| **Naming Conventions** | PASS | `*Entity`, `*JpaRepository`, `*Service`, `*Controller`, `*DtoMapper`, `Persistence*Mapper`. |
| **Code Style** | PASS | Google Java Style, 180-char lines, no wildcard imports, `-Werror`. |
| **API Conventions** | PASS | Endpoints under `/api/v1/`, OpenAPI annotations, `ErrorView` for errors. |
| **Testing Conventions** | PASS | `shouldDoX()` naming, AssertJ, H2 + Testcontainers functional tests. |
| **Multi-Vendor Database** | PASS | Migrations in all three dialect dirs. Dot-separator naming (`V1.55`). |
| **Configuration Defaults** | PASS | New properties in `application.yml` only; no Java field initializers. `PageRequestDto` uses primitive `int` defaults for `page`/`size` — these are request DTO defaults, not `@ConfigurationProperties`. |
| **Anti-Patterns** | PASS | No business logic in entities, no silent swallowing, no `@Transactional` on controllers. |
| **Configuration Documentation** | PASS | Task to update `docs/configuration.md` with new Envers properties. |
| **Schema Documentation** | PASS | Task to run `./gradlew generateDbSchema` after migrations. |

## Project Structure

### Documentation (this feature)

```text
specs/014-auditing/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Research decisions
├── data-model.md        # Entity and migration design
├── quickstart.md        # Implementation guide
├── contracts/
│   └── activities-api.md # REST API contract
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── dao/
│   ├── audit/
│   │   ├── entity/
│   │   │   ├── AuditActivityEntity.java           # Activity feed JPA entity (@Table "audit_activity")
│   │   │   ├── AuditEntityPackage.java            # Package marker for @EntityScan
│   │   │   └── AuditRevisionEntity.java           # Custom REVINFO entity (@RevisionEntity)
│   │   ├── listener/
│   │   │   └── AuditRevisionListener.java         # EntityTrackingRevisionListener
│   │   └── mapper/
│   │       └── AuditActivityMapper.java           # Concrete entity class → ActivityResourceType
│   ├── jpa/
│   │   └── AuditActivityJpaRepository.java        # Spring Data + JpaSpecificationExecutor (scanned via JpaPackage)
│   ├── mapper/
│   │   └── PersistenceAuditActivityMapper.java    # MapStruct: entity → domain
│   ├── entity/
│   │   ├── deployment/
│   │   │   └── DeploymentEntity.java              # MODIFY: add @Audited
│   │   ├── ImageDefinitionEntity.java             # MODIFY: add @Audited
│   │   └── DomainWhitelistEntity.java             # MODIFY: add @Audited
│   └── ...
├── model/
│   └── audit/
│       ├── AuditActivity.java                     # Domain model (uses enum types for activityType/resourceType)
│       ├── ActivityType.java                      # Enum: Create, Update, Delete
│       └── ActivityResourceType.java              # Enum: 11 resource types
├── service/
│   └── audit/
│       └── AuditActivityService.java              # Query service with Specification filtering + field whitelist validation
├── transaction/
│   └── timestamp/
│       ├── TransactionTimestampAspect.java        # AOP @Before on @Transactional
│       └── TransactionTimestampContext.java        # Reads from TransactionSynchronizationManager
├── web/
│   ├── controller/
│   │   └── AuditActivityController.java           # POST /activities, GET /activities/{id}
│   ├── dto/
│   │   └── audit/
│   │       ├── AuditActivityDto.java              # Response DTO
│   │       ├── PageRequestDto.java                # Pagination + filter request body
│   │       └── PageDto.java                       # Paginated response wrapper
│   └── mapper/
│       └── AuditActivityDtoMapper.java            # MapStruct: domain → DTO
└── configuration/
    └── datasource/
        └── JpaConfiguration.java                  # MODIFY: add audit package + DateTimeProvider

src/main/resources/
├── application.yml                                # MODIFY: add Envers properties
└── db/migration/
    ├── H2/V1.55__CreateAuditTables.sql
    ├── POSTGRES/V1.55__CreateAuditTables.sql
    └── MS_SQL_SERVER/V1.55__CreateAuditTables.sql

src/test/java/.../functional/tests/
└── AuditActivityFunctionalTest.java               # Functional tests

docs/
└── configuration.md                               # MODIFY: document new properties
```

**Structure Decision**: New auditing code follows the existing layered architecture. `dao.audit` sub-package groups Envers-specific entities, listener, and mapper. `AuditActivityJpaRepository` and `PersistenceAuditActivityMapper` live alongside existing repos/mappers in `dao.jpa` and `dao.mapper` respectively (scanned by existing package markers, no separate `AuditJpaPackage` needed). `transaction.timestamp` is new cross-cutting infrastructure. Domain model enums in `model.audit` are accessible from both dao and service layers. Page DTOs in `web.dto.audit` since they're specific to the audit feature.

## Complexity Tracking

| Deviation | Why Needed | Alternative Rejected Because |
|-----------|------------|------------------------------|
| Custom `PageRequestDto` instead of Spring `Pageable` in controller | Reference repo uses POST body with complex filter/sort/range criteria; user explicitly requested "same patterns and endpoints" | Spring `Pageable` + `@RequestParam` cannot express range filters or typed filter objects in a POST body; service layer internally uses `PageRequest` (implements `Pageable`) |
| `TransactionTimestampContext`/`Aspect` skip `@LogExecution` | These fire on every `@Transactional` method — logging each invocation would be extremely noisy | Adding `@LogExecution` and then suppressing via log level defeats the purpose; ArchUnit exclusion is explicit |
| `@EnableTransactionManagement(order)` override on `JpaConfiguration` | `TransactionTimestampAspect` must fire INSIDE the transaction (after `TransactionInterceptor` starts it) because `registerSynchronization()` requires an active transaction; without explicit ordering, both default to `LOWEST_PRECEDENCE` making execution order non-deterministic | Relying on Spring's undefined default ordering — works in some environments but not others |
| Primitive `int` for `PageRequestDto.page`/`size` | Explicit JSON `null` for `Integer` fields overwrites the Java default, causing NPE on `PageRequest.of()` auto-unboxing | Wrapper `Integer` with `@NotNull` — adds a validation annotation when the simpler fix is using primitives |
| Field whitelist validation in `AuditActivityService` | User-controlled filter/sort field names passed to JPA Criteria API could cause 500 errors or info leakage | Relying solely on global `IllegalArgumentException` handler — messages from JPA may reveal internal entity structure |
| `@Max(100)` on `PageRequestDto.size` | Unbounded page size is a DoS vector — prevents loading entire activity table into memory | No cap at all, or relying on downstream database/ORM limits — too risky |
| WARN logging in `transactionDateTimeProvider` fallback | Fallback to `Instant.now()` could cause timestamp drift between audit records and `@CreatedDate`/`@LastModifiedDate` fields — WARN makes this visible | Silent fallback — harder to diagnose timestamp inconsistencies in production |
