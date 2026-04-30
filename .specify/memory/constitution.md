<!-- Sync Impact Report
Version change: 1.3.0 → 1.4.0 (MINOR: tech-stack reframe, inline enforcement tags, soften CONFIG_REST_SECURITY_MODE, expanded numbered-spec lifecycle, Out-of-scope + Amendment History sections)
Modified sections:
  - Tech Stack (reframed: build.gradle is canonical for versions; this section pins only constitutionally-significant libs; added ArchUnit, Hibernate Envers, Commons Lang3/Collections4 since rules cite them; documented actuator 3.5.12 as a deliberate BOM override; corrected log4j-core to 2.25.4; corrected Lombok plugin wording; pinned base package)
  - Architecture Principles II (separated `*JpaRepository` from `*Repository` `@Transactional` semantics)
  - Architecture Principles III + Anti-pattern #9 (clarified "polling" = K8s-API polling; scheduled tasks operating on local state are out of scope)
  - Architecture Principles IV (aligned `@LogExecution` Spring-component list with what ArchUnit actually enforces — added `@Repository`, dropped unused `@Controller`)
  - Architecture Principles V (softened production CONFIG_REST_SECURITY_MODE to SHOULD with ops-policy framing; provider list moved to security capability spec)
  - Naming Conventions, Code Style, API Conventions, Anti-Patterns, Patterns, Testing (added inline `(Checkstyle)` / `(ArchUnit)` / `(review)` enforcement tags)
  - Anti-Patterns (deduped #4 to reference Principle II; reworded #5/#9 likewise)
  - API Conventions (strengthened base-path uniqueness to invariants)
  - spec-kit Workflow Rules (added Cancelled / Superseded numbered-spec statuses; added `/speckit.checklist` cleanup guidance)
  - Tooling Commands (added `./gradlew bootRun`)
  - Governance (added Out-of-Scope section, Amendment History footer, enforcement-mechanism legend)
Templates requiring updates:
  ✅ .specify/memory/constitution.md (this file)
  ✅ No template changes needed — speckit reads constitution directly
  ✅ No [PLACEHOLDER] tokens remain
Follow-up TODOs:
  - jjwt 0.9.1 (test-only) is out-of-scope for constitution; track upgrade in dep backlog
-->

# DIAL Deployment Manager Backend Constitution

## Tech Stack

`build.gradle` and `gradle.properties` are the canonical record of dependency versions. This section pins **only the libraries whose presence or version is referenced by a constitutional rule** — anything else lives in the build files and may be bumped without amending this document.

The base package for all production sources MUST be `com.epam.aidial.deployment.manager`.

Constitutionally-significant libraries (any upgrade MUST go through a PR with a security or feature rationale):

- **Runtime**: Java 21, Spring Boot 3.5.10, Gradle 8.13. `spring-boot-starter-actuator` is pinned to 3.5.12 as a **deliberate override** of the BOM; the rationale (security/feature) MUST be recorded in the build file at bump time.
- **Code generation**: Lombok via `io.freefair.lombok` plugin 8.10, MapStruct 1.6.0
- **Database migrations**: Flyway 11.14.0 (`flyway-core` for H2, `flyway-database-postgresql`, `flyway-sqlserver`)
- **Kubernetes**: Fabric8 Kubernetes Client 7.5.2, Fabric8 Knative Client 7.5.2, `io.kubernetes:client-java` 22.0.0
- **Serialization**: Jackson 2.21.1 (enforced via buildscript constraint)
- **Logging**: Log4j2 — `log4j-core` 2.25.4, `log4j-slf4j2-impl` 2.24.3, `log4j-jul` 2.24.3; `spring-boot-starter-logging` MUST be excluded globally
- **Observability**: OpenTelemetry SDK + OTLP exporter, `opentelemetry-spring-boot-starter` 2.12.0, Micrometer + Prometheus
- **Caching**: Caffeine 3.2.3 via `spring-boot-starter-cache`
- **Distributed locking**: ShedLock 6.3.0 (JDBC provider)
- **API docs**: SpringDoc OpenAPI 2.8.5
- **Security**: Spring Security + OAuth2 Resource Server; Azure Identity 1.18.0. The supported OIDC provider list lives in `specs/security/spec.md`.
- **Databases supported**: H2 2.3.232 (dev/test), PostgreSQL 42.7.8, SQL Server 13.2.1 (`mssql-jdbc`)
- **Auditing**: Hibernate Envers — anchors the `auditing` capability spec (numbered feature 014)
- **Code quality**: Checkstyle 10.21.4 (Google Java Style profile), `-Werror` on all Java compilation, ArchUnit 1.3.0 (architectural rules in `ArchitectureTest`)
- **Style helpers**: Apache Commons Lang3 3.18.0 and Commons Collections4 4.5.0-M3 — Code Style mandates their `StringUtils` and `CollectionUtils` over inline null-and-empty checks

## Architecture Principles

### I. Strict Layered Architecture

The codebase MUST follow this dependency direction with no exceptions:

```
web (controller / handler / dto / mapper)
  → service (business logic, @Transactional)
    → dao (entity / repository / jpa / mapper)
    → kubernetes (informer / knative / nim / kserve / event)
```

- `web` layer MUST NOT call `dao` or `kubernetes` directly. — *(ArchUnit)*
- `service` layer MUST NOT be called by `dao` or `kubernetes`. — *(ArchUnit)*
- `kubernetes` package MUST be the only place Fabric8 / `io.kubernetes` API types appear in non-configuration code. — *(review)*
- Cross-layer shortcutting is a blocking PR review comment.

### II. Transactional Discipline

- `@Transactional` MUST only appear on `service`-layer or `dao`-layer classes or their methods.
- Controllers MUST NOT carry `@Transactional`. — *(ArchUnit)*
- Spring Data `*JpaRepository` interfaces inherit framework-managed transactions; manual `@Transactional` is unnecessary and SHOULD NOT be added to them.
- DAO `@Transactional` is permitted on **`*Repository` wrapper methods** (custom query methods that combine multiple JPA operations) where transaction boundaries are inherent to the data access operation. — *(review)*
- Propagation and isolation overrides require an explanatory comment.

### III. Kubernetes Isolation

- All calls that create, update, delete, or read Kubernetes resources MUST live in the `kubernetes/` package. — *(review)*
- Fabric8 informers are the canonical mechanism for watching K8s state changes; **K8s-API polling loops** (repeatedly fetching resource state via the client to detect change) are forbidden. ShedLock-driven scheduled tasks that operate on local state (e.g., cleanup jobs) are not "polling" in this sense.

### IV. Observability First

- `@LogExecution` MUST be placed on every Spring component class — `@RestController`, `@Service`, `@Repository`, `@Component`. — *(ArchUnit: `ArchitectureTest`)*
- All telemetry signals (traces, metrics, logs) MUST flow through OpenTelemetry; direct log statements in business logic are acceptable only for debug-level context.
- Prometheus metrics are exposed via Actuator; custom `MeterRegistry` registrations follow existing patterns in `configuration/`.

### V. Security by Configuration

- Default security mode is `none`; production deployments SHOULD set `CONFIG_REST_SECURITY_MODE` to `oidc` or `basic`. This is **operator policy, not a code-level invariant** — there is no startup guard. The chosen mode MUST be documented in the deployment runbook.
- Sensitive environment variables MUST be converted to Kubernetes Secrets automatically by the pipeline; they MUST NOT be stored in ConfigMaps or committed to git.
- JWT validation is multi-provider: each provider is configured independently under `providers.{name}.*`. The supported provider list lives in `specs/security/spec.md`.

## Naming Conventions

The patterns below are enforced by review unless tagged otherwise.

| Artifact | Pattern | Example | Enforcement |
|---|---|---|---|
| Request/response DTO | `*Dto`, `*RequestDto` | `DeploymentDto`, `CreateDeploymentRequestDto` | review |
| Web layer mapper (MapStruct) | `*DtoMapper` | `DeploymentDtoMapper` | review (MapStruct `componentModel="spring"` → ArchUnit) |
| Persistence mapper (MapStruct) | `Persistence*Mapper` | `PersistenceDeploymentMapper` | review |
| JPA entity | `*Entity` | `DeploymentEntity` | review |
| Spring Data JPA repository | `*JpaRepository` | `DeploymentJpaRepository` | ArchUnit (placement) |
| Domain/DAO repository wrapper | `*Repository` | `DeploymentRepository` | ArchUnit (placement) |
| Service | `*Service` | `DeploymentService` | review |
| Controller | `*Controller` | `DeploymentController` | review |
| Functional / integration test | `*FunctionalTest` | `DeploymentFunctionalTest` | review |
| Unit test | `*Test` | `DeploymentServiceTest` | review |

Package names MUST be all-lowercase with no underscores. — *(Checkstyle: `PackageName`)*

## Code Style

- **Style guide**: Google Java Style. — *(Checkstyle 10.21.4)*
- **Line length**: 180 characters maximum. — *(Checkstyle: `LineLength`)*
- **Compiler**: `-Werror` — all warnings are errors; no suppression without justification. — *(compiler)*
- **Imports**: No wildcard (`*`) imports. Import order: third-party → standard Java → static, alphabetically within groups. — *(Checkstyle: `AvoidStarImport`, `CustomImportOrder`)*
- **FQNs in method bodies**: Forbidden; always use a proper import. — *(review)*
- **Local variables**: SHOULD use `var` where the type is obvious from the right-hand side. — *(review)*
- **Braces**: K&R style; mandatory for all control flow statements. — *(Checkstyle: `LeftCurly`, `NeedBraces`)*
- **Indentation**: 4 spaces; no tabs. — *(Checkstyle: `FileTabCharacter`)*
- **One statement per line** and **one top-level class per file**. — *(Checkstyle: `OneTopLevelClass`)*
- **Collection emptiness checks**: Use `CollectionUtils.isEmpty()` / `CollectionUtils.isNotEmpty()` (`org.apache.commons.collections4.CollectionUtils`) instead of `collection != null && !collection.isEmpty()` or similar inline null-and-empty checks. — *(review)*
- **String emptiness checks**: Use Apache Commons `StringUtils.isEmpty()` / `StringUtils.isNotEmpty()` / `StringUtils.isBlank()` / `StringUtils.isNotBlank()` (`org.apache.commons.lang3.StringUtils`) instead of inline null-and-empty checks on strings. — *(review)*

## API Conventions

- **Public API base path**: `/api/v1/` — no public endpoint may live outside this prefix. — *(review)*
- **Internal API base path**: `/api/internal/v1/` — no internal endpoint may live outside this prefix. — *(review)*
- **Error response schema**: `ErrorView` — MUST include `message`, `status`, and `traceparent` (from OTel span context). Full fields: `path`, `method`, `status`, `error`, `message`, `traceparent`. — *(review)*
- **OpenAPI annotations**: Every endpoint method MUST carry `@Operation` (SpringDoc) with `summary` and relevant `@ApiResponse` annotations. — *(review)*
- **Pagination**: List endpoints returning potentially large datasets MUST support pagination via Spring's `Pageable`. — *(review)*
- **SSE endpoints**: Real-time status updates (builds, deployments) MUST use `SseEmitter` and follow existing patterns in `web/handler/`. — *(review)*
- **Health check**: Standard Spring Boot Actuator at `/actuator/health`; custom health indicators are allowed in `configuration/`.

## Testing Conventions

- **Test method naming**: `shouldDoX()` for happy paths, `shouldFailDoX_whenY()` for failure scenarios. — *(review)*
- **Database selection**: Controlled by `DATASOURCE_VENDOR` env var; Testcontainers for Postgres and SQL Server in CI, H2 for lightweight local runs.
- **Test infrastructure**: Testcontainers; `@Testcontainers` + `@Container` for container lifecycle.
- **Assertions**: AssertJ preferred (`assertThat(...)`); raw JUnit `assertEquals` is discouraged. — *(review)*
- **Functional tests**: MUST extend or mirror `H2FunctionalTests` / `PostgresFunctionalTests` / `SqlServerFunctionalTests` base patterns; cover all supported vendors where SQL behavior may differ. — *(review)*
- **Security tests**: Use `spring-security-test` for authenticated endpoint tests; JWT tokens generated with `io.jsonwebtoken:jjwt`.
- **No mocking of K8s calls in functional tests**: Use Testcontainers or in-memory stubs defined in `src/test/`. — *(review)*

## Key Patterns

**`@LogExecution`** — Class-level annotation MUST appear on every Spring component (`@RestController`,
`@Service`, `@Repository`, `@Component`). Import:
`com.epam.aidial.deployment.manager.configuration.logging.LogExecution`. Wired via
`CustomizableTraceInterceptor` in `configuration/logging/`. *(ArchUnit: `ArchitectureTest`)* — see that
class for the precise set of exclusions and known legacy gaps.

**MapStruct** — All mapper interfaces MUST declare `componentModel = "spring"`. *(ArchUnit)*
`subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION` SHOULD be included
when the mapper handles polymorphic types; it MAY be omitted for simple mappers that don't
involve subclass mappings. *(review)*
```java
@Mapper(componentModel = "spring",
        subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION) // include when mapping polymorphic types
```
Web mappers (`*DtoMapper`) live in `web/mapper/`; DAO mappers (`Persistence*Mapper`) in `dao/mapper/`;
service-level mappers (`*Mapper`) in `mapper/`.

**Lombok** — Standard annotations: `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`
for mutable domain objects and entities; `@Slf4j` for logging (never declare a `Logger` field manually).
`@Value` and Java `record` types are preferred for simple immutable DTOs (e.g., request/response
records where no inheritance is needed).

| Pattern | Mechanism | Location |
|---|---|---|
| Execution logging | `@LogExecution` AOP (see above) | `configuration/logging/` |
| Response caching | Caffeine + `@Cacheable` / `@CacheEvict` | `configuration/`, service layer |
| Distributed job locking | ShedLock `@SchedulerLock` | `service/` scheduled components |
| K8s state watching | Fabric8 informers + handler registrations | `kubernetes/informer/` |
| SQL schema migrations | Flyway `.sql` files per vendor | `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/` |
| Java data migrations | Flyway `.java` callbacks per vendor | `src/main/java/db/migration/{H2,POSTGRES,MS_SQL_SERVER,common}/` |
| JPA DDL | `ddl-auto: validate` only; Flyway owns schema | `configuration/datasource/` |
| Image build pipeline | Multi-step `service/pipeline/step/` chain | `service/pipeline/` |
| Resource lifecycle/cleanup | `cleanup/` package with `@Component` registrations | `cleanup/` |

**Configuration property defaults** — Default values for `@ConfigurationProperties` fields
MUST be declared exclusively in `application.yml` (via `${ENV_VAR:default}` syntax).
Java field initializers in `*Properties` classes MUST NOT duplicate these defaults —
fields MUST be declared without initializers (e.g., `private int maxRetries;`, not
`private int maxRetries = 3;`). This prevents divergence between the YAML and Java
defaults, which is hard to detect and can cause subtle runtime differences depending
on profile loading order. — *(review)*

**Configuration documentation** — `docs/configuration.md` is manually maintained. It MUST be
updated whenever a feature adds new `@ConfigurationProperties` fields, new environment variables,
or changes to existing `application.yml` entries. **Speckit flow**: any feature spec that
introduces or modifies application configuration MUST include a task to update
`docs/configuration.md` with the affected properties — env var name, default value, and
description. — *(review)*

## Multi-Vendor Database Pattern

Database vendor is selected at runtime via the `DATASOURCE_VENDOR` environment variable:
`H2` (default for local/test) | `POSTGRES` | `MS_SQL_SERVER`.

**SQL migrations** — `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`
- Naming: `V{major}.{minor}__{Description}.sql` — e.g. `V1.25__AddAuthorColumnToTables.sql`
- Dot separator between major and minor (standard Flyway format)

**Java migrations** — `src/main/java/db/migration/{H2,POSTGRES,MS_SQL_SERVER,common}/`
- Used for complex data migrations that require Java logic (e.g. JSON transformations)
- Naming: `V{major}_{minor}__{Description}.java` — underscore replaces dot (dots invalid in Java class names)
- `common/` subfolder applies to all vendors

**Flyway configuration**:
- `baseline-on-migrate: true`
- `baseline-version: 1.1`
- JPA `ddl-auto: validate` — Flyway owns schema; Hibernate MUST NOT create or alter tables. — *(review)*

When adding a migration, create the corresponding file in all applicable vendor directories
(H2, POSTGRES, MS_SQL_SERVER) unless the migration is vendor-specific by design.

**Schema documentation** — `docs/db-schema.md` is auto-generated from H2 Flyway
migrations via `./gradlew generateDbSchema`. This file MUST NOT be edited manually.

- A Claude Code hook (`.claude/hooks/generate-db-schema.sh`) auto-triggers regeneration
  when migration files are edited or written during a Claude Code session.
- For non-Claude workflows (manual development, CI), run `./gradlew generateDbSchema`
  after adding or modifying any migration.
- **Speckit flow**: Any feature plan or task list that includes database migrations
  MUST include a final step to run `./gradlew generateDbSchema` and commit the
  updated `docs/db-schema.md`. This ensures the schema doc stays in sync with
  the actual migration state.

## Anti-Patterns

The following MUST NOT appear in new code. PRs introducing these patterns MUST be rejected:

1. **Business logic in entities** — `*Entity` classes are pure data holders; no service calls, computed state, or Lombok `@Builder` default methods with side effects. — *(review)*
2. **Silent exception swallowing** — Every `catch` block MUST either rethrow, log with context, or explicitly document why suppression is safe. — *(review)*
3. **Generic `Exception` catch** — Catch specific exception types. `catch (Exception e)` is allowed only in top-level exception handlers (`DefaultExceptionHandler`). — *(review)*
4. **`@Transactional` on controllers** — See Principle II. — *(ArchUnit)*
5. **Kubernetes API calls from the service layer** — See Principle III. — *(review)*
6. **Wildcard imports** — Violates Code Style. — *(Checkstyle: `AvoidStarImport`)*
7. **Direct `System.out.println` or `e.printStackTrace()`** — Use SLF4J logger via Log4j2. — *(review)*
8. **Hard-coded credentials or secrets** — All secrets via environment variables or Kubernetes Secrets. — *(review)*
9. **K8s-API polling loops** — Use Fabric8 informers. (Scheduled tasks operating on local state, e.g., ShedLock-driven cleanup, are not "polling" in this sense — see Principle III.) — *(review)*
10. **`ddl-auto` values other than `validate`** — Flyway owns schema; JPA must not create or update tables. — *(review)*

## spec-kit Workflow Rules

- **No per-feature checklists**: Do NOT generate `checklists/requirements.md` inside individual feature directories via `/speckit.checklist`. The generic checklist adds no per-feature value and creates redundant files. If `/speckit.checklist` runs and creates a `checklists/` directory, delete it before committing. If a project-wide checklist is needed, maintain a single one in `specs/`.

- **Numbered-spec lifecycle**: The `**Status**:` field on `specs/NNN-<short-name>/spec.md` MUST take one of the following values:
  - `Draft` — work in progress.
  - `Implemented` — all P1+ stories shipped; matching capability spec(s) updated with `Implemented via NNN-<feature>` cross-reference.
  - `Partially implemented — <reason>` — some stories deliberately deferred.
  - `Cancelled — <reason>` — feature was dropped before shipping. Spec is retained as audit trail; no capability cross-link is created. Any `Pending: NNN-<feature>` notes in capability specs MUST be removed.
  - `Superseded by NNN-<short-name>` — feature was rolled into or replaced by a later numbered spec. The successor spec MUST cross-link back to the predecessor. Once the successor's behavior is the default path, the affected capability spec is **rewritten** (not annotated) to describe only the current state.

- **Capability ↔ numbered cross-links**: Numbered specs MUST declare a `**Capability**:` field directly under `**Status**:` (the convention is recorded in root `CLAUDE.md`). When a numbered feature ships, the matching capability spec MUST be updated and gain an `Implemented via NNN-<feature>` reference near the affected Requirement. Capability specs MAY reference in-flight numbered specs as `Pending: NNN-<feature>` notes; these MUST be removed or upgraded to `Implemented via …` when the feature ships, or removed when the feature is `Cancelled`.

## Tooling Commands

| Command | Purpose |
|---|---|
| `./gradlew testFast` | Run all tests except Postgres and SQL Server testcontainers functional tests — **use this during development** |
| `./gradlew test` | Run the full test suite including all testcontainers functional tests |
| `./gradlew checkstyleMain checkstyleTest` | Run Checkstyle on main and test sources |
| `./gradlew clean build` | Full clean build (tests + Checkstyle) |
| `./gradlew clean bootJar` | Build executable JAR without running tests |
| `./gradlew bootRun` | Run the service locally on the JVM |
| `./gradlew generateDbSchema` | Regenerate `docs/db-schema.md` from H2 Flyway migrations — run after any migration change |
| `docker build -t deployment-manager .` | Build Docker image (two-stage: `gradle:8.13-jdk21-alpine` → `amazoncorretto:21-alpine`) |

After any code change, always verify with `./gradlew checkstyleMain checkstyleTest` before
considering the task done. Use `./gradlew testFast` to run tests during development; a clean
`./gradlew clean build` (full suite) is the gate for PR readiness.

## Out of Scope

This constitution governs the backend Java service. The following areas are intentionally **not** covered here and have their own sources of truth:

- Frontend / UI conventions
- Deployment topology, infrastructure-as-code, and ops runbooks
- CI/CD pipeline mechanics (Jenkinsfile, GitHub Actions workflows)
- Per-environment configuration values (live in deployment manifests, not in this repo)
- Third-party library versions beyond the constitutionally-significant set above — `build.gradle` and `gradle.properties` are canonical

Capability-level behavior, contracts, and invariants live in `specs/<capability>/spec.md`; the constitution intentionally avoids restating them.

## Governance

This constitution is the authoritative source of architectural truth for the DIAL Deployment Manager Backend. It supersedes any conflicting guidance in PR comments, local convention notes, or informal team agreements.

Per-directory `CLAUDE.md` files exist for each architectural layer (`web/`, `service/`, `dao/`, `kubernetes/`, `configuration/`) and the migration trees; they restate this constitution's rules in local form and add subpackage navigation. The constitution remains the single source of truth — local files MUST NOT contradict it.

**Amendment procedure**:
1. Raise a PR with changes to this file; at least one approval required.
2. Include a migration note if the amendment changes existing code patterns (link to a follow-up issue for affected code).
3. Update the Sync Impact Report block at the top of this file and append a row to the Amendment History table below.
4. Run `/speckit.constitution` if the amendment affects content speckit reads directly (currently this file only).

**Versioning policy**:
- MAJOR: Removal or redefinition of an existing principle (breaks existing code).
- MINOR: New principle, section, or materially expanded guidance.
- PATCH: Clarifications, wording, typo fixes, non-semantic refinements.

**Compliance review**: All PRs MUST be checked against this constitution. Inline enforcement tags identify the mechanism for each rule:
- *(Checkstyle)* / *(compiler)* — blocked at build time.
- *(ArchUnit)* — verified by `ArchitectureTest`, runs as part of the test suite.
- *(review)* — relies on PR review; no automated check.

## Amendment History

| Version | Date | Summary |
|---|---|---|
| 1.4.0 | 2026-04-30 | Reframed Tech Stack (build.gradle canonical); added inline enforcement tags; softened CONFIG_REST_SECURITY_MODE to SHOULD; aligned `@LogExecution` Spring-component list with ArchUnit; deduped Anti-pattern #4; clarified K8s-API polling vs scheduled tasks; expanded numbered-spec lifecycle (Cancelled, Superseded); added Out of Scope and Amendment History sections |
| 1.3.0 | 2026-04-29 | Per-directory CLAUDE.md acknowledgment; added Capability ↔ numbered-spec cross-link rule |
| ≤1.2.2 | — | See `git log .specify/memory/constitution.md` |

**Version**: 1.4.0 | **Ratified**: 2026-03-04 | **Last Amended**: 2026-04-30
