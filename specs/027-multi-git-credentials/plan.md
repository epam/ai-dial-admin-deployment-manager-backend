# Implementation Plan: Multiple Git Credentials per Domain

**Branch**: `027-multi-git-credentials` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/027-multi-git-credentials/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Operators today can configure only one trusted-git-repo credential per host via `TRUSTED_PRIVATE_GIT_REPOS` (`GitProperties.TrustedPrivateGitRepo`: host + auth material). This feature extends that same static-config mechanism to allow multiple credential entries per host, each additionally scoped by an optional repository/project path, and makes `GitService`'s credential resolution pick the single most-specific matching entry (repository-exact > project/group-prefix > domain-wide, with an exact-host match always beating a subdomain-inherited match). No new persisted entity, API, or UI is introduced — this is a configuration-schema and matching-algorithm change confined to `configuration/` and `service/pipeline/specification/GitService.java`.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.0.7 (`@ConfigurationProperties`-style bean wiring, though this feature keeps the existing manual `@Bean` + Jackson parsing approach in `GitConfiguration`); Jackson 3 (`tools.jackson`) for parsing the `TRUSTED_PRIVATE_GIT_REPOS` JSON payload — already in use, no new dependency
**Storage**: N/A — configuration stays in-memory, sourced from the `TRUSTED_PRIVATE_GIT_REPOS` env var at startup; no database entity or migration (per spec FR-008)
**Testing**: JUnit 5 + AssertJ (constitution testing conventions); unit tests only — `GitConfigurationTest` for parsing/validation, `GitServiceTest` for precedence resolution. No functional/testcontainers test needed since no new endpoint, entity, or DB behavior is introduced.
**Target Platform**: Linux server (existing Spring Boot service, unchanged deployment topology)
**Project Type**: Single project (backend service) — no new module, no frontend/UI change
**Performance Goals**: N/A — credential resolution runs at most once per image build, over an operator-sized list (tens of entries); no measurable performance target beyond "negligible relative to a git clone"
**Constraints**: Existing single-entry-per-host configurations MUST continue to work unchanged after upgrade (spec SC-005); no behavior change for hosts with zero or one credential entry
**Scale/Scope**: Configuration list size is operator-managed (expected: low tens of entries); no scale requirement beyond today's

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Strict Layered Architecture**: All changes stay within `configuration/` (`GitProperties`, `GitPropertiesDto`, `GitConfiguration`) and `service/pipeline/specification/GitService.java`. No new cross-layer calls; `web`/`dao`/`kubernetes` are untouched. **PASS**.
- **Kubernetes Isolation**: No change to how `GitSecretConfig`/Kubernetes Secrets are built — only which `TrustedPrivateGitRepo` is selected as input changes. **PASS**.
- **Configuration property defaults**: The new `path` field on `TrustedPrivateGitRepoDto`/`TrustedPrivateGitRepo` has no meaningful default (absence = domain-wide scope) and is not a `${ENV_VAR:default}`-style scalar property — it's a per-entry field inside a JSON list, consistent with how `host`/`user`/`token` etc. are already handled (no Java initializer, no YAML default). **PASS**.
- **Configuration documentation**: `docs/configuration.md` MUST be updated for the new `path` field on `TRUSTED_PRIVATE_GIT_REPOS` — tracked as a task. **Gate satisfied via task, not yet done.**
- **No new DB entity/migration**: Confirmed by spec FR-008; no Flyway migration, no `docs/db-schema.md` regeneration needed. **PASS**.
- **No new API endpoint**: Confirmed by spec FR-008; no OpenAPI/pagination/SSE conventions apply. **PASS**.
- **Testing conventions**: New/extended unit tests only (`GitConfigurationTest`, `GitServiceTest`), AssertJ assertions, `shouldDoX()` / `shouldFailDoX_whenY()` naming. **PASS** (to be verified at implementation).
- **Anti-patterns**: No hard-coded secrets, no generic `Exception` catches introduced beyond the existing pattern in `GitConfiguration`/`GitService` (both already catch broadly only at the JSON-parse / URL-parse boundary, consistent with existing code). **PASS**.

No violations identified. Complexity Tracking section is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/027-multi-git-credentials/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── trusted-private-repos-schema.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Option 1: Single project (this repo's existing structure — no new modules)
src/main/java/com/epam/aidial/deployment/manager/
├── configuration/
│   ├── GitProperties.java              # add optional `path` field to TrustedPrivateGitRepo
│   ├── GitPropertiesDto.java           # add optional `path` field to TrustedPrivateGitRepoDto
│   └── GitConfiguration.java           # extend validation: duplicate-scope (host+path) detection; unchanged per-entry auth-material rules
└── service/pipeline/specification/
    └── GitService.java                 # replace first-match lookup with specificity-ranked resolution across all matching entries

src/test/java/com/epam/aidial/deployment/manager/
├── configuration/
│   └── GitConfigurationTest.java       # new: parsing + duplicate-scope validation tests
└── service/pipeline/specification/
    └── GitServiceTest.java             # new: precedence resolution tests (repo > project > domain; exact-host > subdomain)

docs/
└── configuration.md                    # document the new `path` field on TRUSTED_PRIVATE_GIT_REPOS entries
```

**Structure Decision**: Single-project structure (this is a Spring Boot monolith backend; no frontend or additional services are affected). All changes are additive modifications to three existing files in `configuration/` and one existing file in `service/pipeline/specification/`, plus their corresponding test files and `docs/configuration.md`. No new packages or architectural layers are introduced.

## Complexity Tracking

*No Constitution Check violations — this section is not applicable.*
