# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Source of Truth

All architecture rules, naming conventions, code style, patterns, and domain knowledge live in speckit docs. **Read these before making changes:**

- **Constitution** (mandatory): `.specify/memory/constitution.md` — layered architecture, naming conventions, tech stack versions, key patterns, testing strategy
- **Feature specs**: `specs/<feature>/spec.md` — domain behavior, API contracts, edge cases for each feature area
- **Docs**: `docs/configuration.md` (env vars)

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
