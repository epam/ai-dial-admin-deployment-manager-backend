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

## Active Technologies
- Java 21, Spring Boot 3.5.10, Gradle 8.13 + Fabric8 Kubernetes Client 7.5.2 (provides `LocalPortForward`), NVIDIA NIM CRD (NIMService), gRPC 1.75.0 (`grpc-netty-shaded`, `grpc-stub`, `grpc-protobuf`), Protobuf 3.25.3 (`protobuf-java`), com.google.protobuf Gradle plugin 0.9.4, MapStruct 1.6.0, Lombok 8.10
- H2 2.3.232 (dev/test), PostgreSQL 42.7.8, SQL Server 13.2.1 — Flyway migrations V1.59 and V1.60 across all three vendors (018-hubble-relay-domains)

## Recent Changes
- 013-nim-served-model-name
- 018-hubble-relay-domains
