# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Source of Truth

All architecture rules, naming conventions, code style, patterns, and domain knowledge live in speckit docs. **Read these before making changes:**

- **Constitution** (mandatory): `.specify/memory/constitution.md` — layered architecture, naming conventions, tech stack versions, key patterns, testing strategy
- **Capability specs**: `specs/<capability>/spec.md` — per-area behavior and contracts; index in `specs/README.md`
- **Numbered feature specs**: `specs/NNN-<feature>/` — speckit workflow artifacts (spec, plan, tasks) for in-flight features
- **Per-directory CLAUDE.md**: each layer (`web/`, `service/`, `dao/`, `kubernetes/`, `configuration/`) and migration tree has a local CLAUDE.md with layer-specific rules and pointers to relevant capability specs. Read the one in the directory you're editing first.
- **Docs**: `docs/configuration.md` (env vars), `docs/db-schema.md` (auto-generated, do not edit)

## Essential Commands

```bash
./gradlew testFast                      # Dev testing — H2 only, fast feedback
./gradlew test                          # Full suite — includes Postgres/SQL Server via testcontainers
./gradlew checkstyleMain checkstyleTest # Code style (Google Java Style, 180-char lines)
./gradlew clean build                   # Full clean build
./gradlew bootRun                       # Run locally
```

Run a single test class:
```bash
./gradlew testFast --tests "com.epam.aidial.deployment.manager.functional.h2.DeploymentFunctionalTest"
```

## Spec-Driven Development

This project uses speckit (`/speckit.*` slash commands) for feature development. The workflow is: specify → plan → tasks → implement. Feature specs in `specs/` and the constitution in `.specify/memory/constitution.md` are the authoritative references for how code should be structured.

### Spec maintenance is part of every code change

When you change behaviour, contracts, or invariants documented in any `specs/<capability>/spec.md`, update that spec in the same change — it isn't optional polish. This applies to both speckit-driven work and ad-hoc edits.

**Before reporting a code change as complete:**
1. Skim `specs/README.md` to identify which capability spec(s) describe the area you touched.
2. If a capability spec mentions the API/behaviour/invariant you changed, update the spec to match the new state.
3. If you've checked and no spec update is needed, say so explicitly in your final summary so the user doesn't have to ask.

### Speckit-vendored files are off-limits

The following paths are vendored from github/spec-kit and **MUST NOT** be edited — they get overwritten on `specify update`:

- `.specify/templates/*.md`
- `.specify/extensions.yml`
- `.specify/scripts/bash/*.sh`
- `.claude/skills/speckit-*/SKILL.md`

When you need to extend speckit behaviour (e.g. add metadata, change a workflow step), put the convention in user-owned files: root `CLAUDE.md`, `.specify/memory/constitution.md`, the `specs/` tree, or new (non-`speckit-*`-prefixed) skills/hooks under `.claude/`.

### Numbered-spec hygiene

Numbered feature specs under `specs/NNN-<short-name>/` are managed by speckit but the metadata Claude needs lives in user-owned conventions:

**After running `/speckit.specify`**, immediately edit the freshly-created `specs/NNN-<short-name>/spec.md` to add a line directly under `**Status**: Draft`:

```
**Capability**: <slug>
```

Where `<slug>` is the directory name under `specs/` that the feature belongs to (e.g. `nim-deployments`, `image-builds`, `mcp-deployments`). Conventions:
- For cross-cutting features that touch several capabilities, use a comma-separated list: `**Capability**: deployments, kubernetes-manifests`.
- For features that introduce a brand-new capability: `**Capability**: N/A — creates new capability <new-slug>`.
- Use `specs/README.md` as the source of valid slugs.

**When `/speckit.implement` finishes**, before reporting complete:
1. Edit `specs/NNN-<short-name>/spec.md` to flip `**Status**:` from `Draft` to `Implemented` (or `Partially implemented — <reason>` if not all P1+ stories landed).
2. Read the `**Capability**:` line; for each listed slug, open `specs/<slug>/spec.md` and update the affected Requirement(s)/Scenario(s) to match the shipped behaviour. Add a one-line `Implemented via NNN-<feature>` cross-reference near the changed Requirement, and remove or upgrade any `Pending: NNN-<feature>` note for this feature.
3. If the field is `N/A — creates new capability <slug>`, scaffold `specs/<slug>/spec.md` from the house style (use `specs/api-conventions/spec.md` as a model) and add a row to `specs/README.md`.

## Active Technologies
- Java 21 + Spring Boot 4.0.6 (Spring Framework 7); Fabric8 Kubernetes Client 7.5.2 + Knative Client 7.5.2; KServe CRD model (`io.kserve.serving.v1beta1`); Jib `ImageReference` for image parsing; MapStruct 1.6.3; Lombok (025-auto-pull-secrets)
- No new schema. Reuses the existing `DisposableResource` table (`K8sResourceKind.SECRET`). Multi-vendor (H2/PostgreSQL/SQL Server) unaffected — no Flyway migration, no `generateDbSchema` run required. (025-auto-pull-secrets)
- Java 21 + Spring Boot 4.0.6 (Framework 7), Hibernate ORM 7 + Envers, MapStruct 1.6.3, Flyway, Fabric8/KServe client, Lombok (024-model-serving-capability)
- Relational, multi-vendor (H2 / PostgreSQL / SQL Server) via Flyway migrations; `inference_deployment` table (024-model-serving-capability)

## Recent Changes
- 025-auto-pull-secrets: Added Java 21 + Spring Boot 4.0.6 (Spring Framework 7); Fabric8 Kubernetes Client 7.5.2 + Knative Client 7.5.2; KServe CRD model (`io.kserve.serving.v1beta1`); Jib `ImageReference` for image parsing; MapStruct 1.6.3; Lombok
- 024-model-serving-capability: Added Java 21 + Spring Boot 4.0.6 (Framework 7), Hibernate ORM 7 + Envers, MapStruct 1.6.3, Flyway, Fabric8/KServe client, Lombok
