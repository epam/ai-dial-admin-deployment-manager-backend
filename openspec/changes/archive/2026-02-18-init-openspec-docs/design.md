## Context

The project has a working `openspec/` directory skeleton but no content — `config.yaml` holds only the `schema: spec-driven` key, and `specs/` is empty. No AGENTS.md or architectural documentation for AI tools exists. The project is a Spring Boot 3.5.10 / Java 21 Kubernetes deployment manager with a well-established layered architecture and 9 controllers covering 23 distinct capabilities.

A reference project (`ai-dial-admin-evaluation-framework-backend`) already runs a mature OpenSpec setup and provides proven patterns for `config.yaml` structure, spec format, and enforcement rules. Key structural differences from the reference must be adapted here.

## Goals / Non-Goals

**Goals:**
- Produce a `config.yaml` that accurately encodes this project's architecture, naming conventions, enforcement rules, and maintenance policies
- Produce one spec file per capability covering all currently-implemented functionality
- Produce a `specs/README.md` index so humans and tools can navigate capabilities
- Enable future feature work to use the full `/opsx:new` → `/opsx:apply` → `/opsx:archive` workflow from day one

**Non-Goals:**
- Documenting planned/unimplemented features (no "Vision" section)
- Modifying any application source code
- Creating AGENTS.md (a separate future change can own that)

## Decisions

### D1 — Adapt config.yaml from reference, not from scratch

**Decision:** Use the reference project's `config.yaml` structure as a template, adapting sections to this project's stack and conventions.

**Rationale:** The reference project's config has proven effective over 12+ archived changes. Re-deriving structure from scratch risks omitting useful sections (maintenance policies, per-artifact rules, code examples, anti-patterns).

**Key adaptations required:**
- Architecture layer: `dao/` (JPA/Hibernate) instead of `data.db/` (pure JDBC). The `dao/` layer has four sub-packages: `dao/entity/` (JPA entities), `dao/jpa/` (JPA repository interfaces extending `JpaRepository`), `dao/repository/` (custom repository wrappers with domain mapping), `dao/mapper/` (entity-to-domain MapStruct mappers)
- No RowMapper pattern — entities use `@Entity` JPA annotations; JPA interfaces live in `dao/jpa/`, custom wrappers in `dao/repository/`
- `@Transactional` still at service layer (same principle)
- Multi-vendor database (H2 / PostgreSQL / SQL Server) instead of PostgreSQL-only
- MapStruct mappers for DTO↔entity conversion (reference used manual mapping)
- No ArchUnit `LayeredArchitectureTest` — layering is a convention, not enforced by tests
- `ErrorView` has `traceparent` (W3C Trace Context) instead of `code` enum + `details`
- Pagination: Spring Data `Page<T>` / `PageResponse` for most endpoints; cursor-based for HuggingFace

### D2 — Per-type deployment specs with a shared base spec

**Decision:** Create one `deployments` spec for the common CRUD/lifecycle contract, plus separate specs for each subtype: `mcp-deployments`, `interceptor-deployments`, `adapter-deployments`, `inference-deployments`, `nim-deployments`.

**Rationale:** All deployment types share the same controller (`DeploymentController`) and base entity fields, but differ significantly in configuration, Kubernetes backend (KNative vs NIM vs KServe), and feature set.

**Important hierarchy:** The Java DTO layer has an intermediate abstract class `ImageBasedDeploymentDto` (adds `imageDefinitionId`, `imageDefinitionName`, `imageDefinitionVersion`) that MCP, Interceptor, and Adapter all extend. Inference and NIM extend `DeploymentDto` directly and use model sources instead. This split is documented in the `deployments` spec as two deployment families:
- **Image-based family** (MCP, Interceptor, Adapter): requires `imageDefinitionId`; K8s backend is KNative
- **Model-source family** (Inference, NIM): references model source (HuggingFace / NGC); K8s backends are KServe / NIM

`InterceptorDeploymentDto` and `AdapterDeploymentDto` are empty subclasses of `ImageBasedDeploymentDto` — their subtype specs explicitly document that no additional fields exist beyond the ImageBased contract.

**Impact:** 6 deployment specs total. The `deployments` base spec documents common fields, operations, and lifecycle; subtype specs document type-specific fields and behaviors.

### D3 — @LogExecution convention with known gaps

**Decision:** Adopt the `@LogExecution` rule from the reference project — all Spring components (`@RestController`, `@Service`, `@Repository`, `@Component`, `@Configuration`) SHOULD have `@LogExecution` at the class level. Document this as an established convention rather than a strict invariant.

**Rationale:** The annotation exists in this project at the same import path (`com.epam.aidial.deployment.manager.configuration.logging.LogExecution`). Most components follow this convention, but 5 controllers currently lack it (`TopicController`, `GlobalDomainWhitelistController`, `HealthController`, `McpController`, `DisposableResourceController`). New code MUST follow the convention; existing gaps can be addressed incrementally.

### D4 — Organize specs into 5 concern groups

**Decision:** Group capabilities as: Core Domain, Kubernetes Integration, Cross-cutting Concerns, Infrastructure, External Integrations.

**Rationale:** The reference uses Core Domain / Integration / Cross-cutting / Analytics / Infrastructure / Vision. This project has no Analytics datasource but has a significant Kubernetes Integration category and External Integrations category that warrant their own groups. This structure maps naturally to the domain.

### D5 — Document multi-vendor database strategy in config.yaml

**Decision:** Capture the multi-vendor database pattern (H2 for tests, PostgreSQL for production, SQL Server as alternative) and the Flyway migration layout per vendor in the config.yaml context section.

**Rationale:** The multi-vendor support affects migration naming (`db/migration/H2/`, `db/migration/POSTGRES/`, `db/migration/MS_SQL_SERVER/`), testing setup, and `DATASOURCE_VENDOR` configuration. This is a project-wide architectural fact that all artifact generation must be aware of.

### D6 — No AGENTS.md maintenance rules in this change

**Decision:** Omit AGENTS.md from this change's scope. The `tasks.md` will not include a task to create or update AGENTS.md.

**Rationale:** AGENTS.md doesn't exist yet. Creating it in the same change as 23 spec files would make the scope too broad. A focused follow-up change can own AGENTS.md initialization.

## Risks / Trade-offs

- **[Risk] Spec accuracy** — specs are written from code inspection rather than from living requirements documents. Some edge cases may be inaccurate. → Mitigation: mark all specs as `Status: Implemented` and rely on code review + tests to validate scenarios.
- **[Trade-off] Breadth over depth** — 23 specs means each one is necessarily concise at initialization. Some capabilities (e.g., `kubernetes-manifests`) have complex internal logic that a single spec cannot fully capture. → Accepted: specs can be extended via future changes as deeper requirements are discovered.
- **[Risk] Spec drift** — without ArchUnit or similar enforcement, the layering convention documented in config.yaml is not automatically tested. → Mitigation: document it clearly; a future change can introduce ArchUnit tests.
