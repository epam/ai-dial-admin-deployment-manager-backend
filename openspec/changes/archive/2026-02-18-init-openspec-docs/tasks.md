## 1. Populate openspec/config.yaml

- [x] 1.1 Replace the placeholder `openspec/config.yaml` with the full project context: tech stack (Java 21, Spring Boot 3.5.10, Gradle, Spring Data JPA / Hibernate, Flyway, Fabric8 K8s client 7.5.2, BuildKit, Skopeo, MCP SDK, Log4j2 + OpenTelemetry, SpringDoc OpenAPI 2.8.5, MapStruct, Caffeine, ShedLock, Azure Identity SDK)
- [x] 1.2 Add the layered architecture section: `web` (controllers, DTOs, validation, OpenAPI, `web/mapper/` for DTO mappers), `service` (domain logic, `@Transactional`), `dao` (four sub-packages: `dao/entity/` for JPA entities, `dao/jpa/` for JPA repository interfaces, `dao/repository/` for custom repository wrappers with domain mapping, `dao/mapper/` for entity-to-domain mappers; no business logic), `kubernetes` (K8s client, informers, manifest generation), `configuration` (Spring config, security, datasource, logging)
- [x] 1.3 Add naming conventions: DTO suffix (`RequestDto`/`ResponseDto`), Mapper suffix, Repository suffix, Service suffix, Controller suffix, Entity suffix; package conventions; `var` usage; camelCase/UPPER_SNAKE_CASE constants
- [x] 1.4 Add code style: Google Java Style Guide enforced by Checkstyle; max line length 180; 4-space indentation; no FQNs in method bodies
- [x] 1.5 Add API conventions: versioning under `/api/v1/`; `ErrorView` fields (`path`, `method`, `status`, `error`, `message`, `traceparent`); error message sanitization (max 2000 chars, no stack traces)
- [x] 1.6 Add testing conventions: TestContainers for integration tests; `shouldDoX()` / `shouldDoX_whenY()` naming; multi-vendor DB tests (`DATASOURCE_VENDOR`); `./gradlew test` runs all tests
- [x] 1.7 Add multi-vendor database pattern: `DATASOURCE_VENDOR` selects H2 / POSTGRES / MS_SQL_SERVER; Flyway paths per vendor (`db/migration/H2/`, `db/migration/POSTGRES/`, `db/migration/MS_SQL_SERVER/`); migration naming `V{major}.{minor}__{Description}.sql`
- [x] 1.8 Add key patterns: `@Transactional` at service layer; `@LogExecution` on Spring components (convention — required for all new code; some older controllers lack it); MapStruct for DTO↔entity mapping; Lombok for boilerplate; Caffeine for in-memory caching; ShedLock for distributed scheduler locks; Kubernetes informers for cluster state
- [x] 1.9 Add anti-patterns to avoid: parsing/business logic in `dao` entities; silent exception swallowing; catching generic `Exception`/`Throwable`; magic numbers in annotations; defaults in `@ConfigurationProperties` Java fields; FQNs in code
- [x] 1.10 Add tooling commands: `./gradlew test`, `./gradlew checkstyleMain checkstyleTest`, `./gradlew clean build`, Docker build command
- [x] 1.11 Add Feature Surface section referencing `openspec/specs/README.md`
- [x] 1.12 Add Config Maintenance Policy (verbatim from reference — same litmus test applies)
- [x] 1.13 Add Spec Index Maintenance Policy (verbatim from reference)
- [x] 1.14 Add per-artifact `rules` section with the following exact content:
  - `global`:
    - All new Spring components (`@RestController`, `@Service`, `@Repository`, `@Component`, `@Configuration`) MUST have `@LogExecution` at the class level. Import: `com.epam.aidial.deployment.manager.configuration.logging.LogExecution`. Note: some older controllers lack this annotation — new code must always include it.
    - Never silently swallow exceptions; at minimum log at WARN+ with full exception — `log.error("msg: {}", e.getMessage(), e)`. Pass exception as last SLF4J argument.
    - Use fail-fast (throw) for serialization/data integrity errors. Use graceful degradation (log + fallback) only for regenerable data.
    - Catch specific exceptions, not generic `Exception`/`Throwable`.
    - No code duplication; extract shared patterns to utility classes.
    - No FQNs in method bodies, signatures, or annotation attributes (Checkstyle-enforced).
    - All `@ConfigurationProperties` defaults MUST be defined in `application.yml`, not as Java field initializers.
    - Non-configurable constants MUST be defined once in a constants class per bounded context. No duplicate definitions.
    - Follow the layering principle: `web`/`service` parse and validate; `dao` entities are pure carriers; `dao` repositories own all persistence; `kubernetes` package owns all K8s client usage. No business logic in `dao` entities, no K8s calls from `service`.
    - When changing API endpoints or DTOs, update OpenAPI annotations (`@Schema`, `@Operation`, `@ApiResponse`).
    - When adding Flyway migrations, update `docs/configuration.md` if schema changes affect configuration docs.
    - Implementation scope limit: do NOT implement more than 5 tasks or more than 1 task group per iteration. Stop and ask for confirmation before the next group. Exception: user explicitly asks to implement everything.
    - After implementation, check whether the change alters project-wide conventions or architecture per the Config Maintenance Policy. If yes, update `config.yaml`. If the change only adds features following existing patterns, do NOT update `config.yaml`.
    - After implementation, check whether `openspec/specs/README.md` needs updating per the Spec Index Maintenance Policy (new spec folder, status change, summary inaccurate). Update as part of the same change.
  - `proposal`:
    - Use sections: Why, What Changes, Capabilities (New / Modified), Impact.
    - Explicitly list which specs will be created (New Capabilities) or modified (Modified Capabilities). Each capability name must match the kebab-case folder name.
    - Call out breaking changes, new packages/classes, DB migration requirements, and config property changes.
  - `specs`:
    - Each requirement MUST start with `### Requirement: <name>`.
    - Each scenario MUST start with `#### Scenario: <name>` with WHEN/THEN bullets (exactly 4 hashtags).
    - Every requirement MUST have at least one scenario. Mark status per requirement: `Status: **Implemented**` or `Status: **Planned**`.
    - Keep requirements testable and non-ambiguous. Include an Implementation Notes section linking to relevant code paths.
    - For new full specs (not deltas): use `## Requirements` header (not `## ADDED Requirements`).
  - `design`:
    - Align with the layered architecture defined in context.
    - Include: data model changes, API contract details, transaction boundaries, error handling approach, component interaction.
    - For each key decision: state the decision, rationale, alternatives considered, and impact.
  - `tasks`:
    - Write tasks as checkbox list (`- [ ]`) with small, independently completable items referencing specific file/package and a clear done condition.
    - Include cross-cutting tasks when applicable: tests, Checkstyle (`./gradlew checkstyleMain checkstyleTest`), Flyway migrations, `docs/configuration.md` update, OpenAPI annotations.
    - Always include a Verification section as the last task group.
    - If the change introduces new architecture, cross-cutting convention, or tooling, include: `- [ ] Update openspec/config.yaml per Config Maintenance Policy`.
    - For feature work, include: `- [ ] Update openspec/specs/README.md per Spec Index Maintenance Policy`.
  - `archive`:
    - Before archiving, verify: all tasks completed, `./gradlew clean build` passes, Checkstyle passes, docs updated.
    - Ensure all implementation notes in specs point to code that actually exists.
    - Verify specs are up to date with implemented requirements.
    - `specs/README.md` auto-sync (ALWAYS perform): list all folders under `openspec/specs/`; add missing entries; remove phantom entries.
    - `config.yaml` review: if the change introduced new packages, layers, conventions, or tooling, update the relevant sections per Config Maintenance Policy.

## 2. Create openspec/specs/README.md

- [x] 2.1 Create `openspec/specs/README.md` with introduction section explaining the purpose of the specs directory and the change workflow
- [x] 2.2 Add **Core Domain** section with entries for: `image-definitions`, `mcp-image-definitions`, `interceptor-image-definitions`, `adapter-image-definitions`, `image-builds`, `deployments`, `mcp-deployments`, `interceptor-deployments`, `adapter-deployments`, `inference-deployments`, `nim-deployments`, `mcp-servers`, `topics`, `domain-whitelist` — each with status Implemented and a one-line summary
- [x] 2.3 Add **Kubernetes Integration** section with entries for: `kubernetes-manifests`, `kubernetes-events`, `kubernetes-cleanup` — each with status Implemented and a one-line summary
- [x] 2.4 Add **Cross-cutting Concerns** section with entries for: `security`, `api-conventions`, `observability-and-logging` — each with status Implemented and a one-line summary
- [x] 2.5 Add **Infrastructure** section with entries for: `database-and-migrations`, `health` — each with status Implemented and a one-line summary
- [x] 2.6 Add **External Integrations** section with entries for: `container-registry`, `huggingface`, `buildkit` — each with status Implemented and a one-line summary

## 3. Sync delta specs to openspec/specs/

- [x] 3.1 Run `/opsx:sync` to copy all 25 capability specs from `openspec/changes/init-openspec-docs/specs/` to `openspec/specs/` — verify each folder exists at the correct path after sync

## 4. Verification

- [x] 4.1 Verify `openspec/config.yaml` is valid YAML and the `context` block renders without errors (`openspec status` shows no config errors)
- [x] 4.2 Verify `openspec/specs/README.md` lists all 25 capabilities with no broken links
- [x] 4.3 Verify all 25 spec files exist under `openspec/specs/` after sync
- [x] 4.4 Run `openspec status --change init-openspec-docs` and confirm the change shows as apply-complete
- [x] 4.5 Confirm the project builds without errors: `./gradlew clean build` (no source changes expected, but verify nothing was accidentally modified)
