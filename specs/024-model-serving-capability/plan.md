# Implementation Plan: Model Serving Capability API

**Branch**: `024-model-serving-capability` | **Date**: 2026-06-29 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/024-model-serving-capability/spec.md`

## Summary

Teach inference-task detection to recognise **text-generation** models (today it only yields
`TEXT_CLASSIFICATION` or `NONE`), persist the detected task on the inference deployment, and expose
it read-only on the existing inference deployment API responses. The frontend maps the value to a
consumption surface: `TEXT_GENERATION` → chat completion, `TEXT_CLASSIFICATION` → MCP toolset,
`NONE` → neither.

Technical approach: extend the `InferenceTask` enum + `InferenceTaskDetector`; add a persisted
`inferenceTask` column to `inference_deployment`; compute and persist the task at create and on
source-changing update via a type-specific enrichment hook on the inference deployment manager;
serialise the persisted value through the existing `InferenceDeploymentDto` / `DeploymentDtoMapper`.
The deploy-time manifest path (`prepareServiceSpec` → transformer chaining + `id2Label`) is
unchanged.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 4.0.6 (Framework 7), Hibernate ORM 7 + Envers, MapStruct 1.6.3, Flyway, Fabric8/KServe client, Lombok
**Storage**: Relational, multi-vendor (H2 / PostgreSQL / SQL Server) via Flyway migrations; `inference_deployment` table
**Testing**: JUnit + AssertJ; `*FunctionalTest` over H2 (`testFast`) and Postgres/SQL Server testcontainers (`test`)
**Target Platform**: Linux server (Spring Boot service), Kubernetes-deployed
**Project Type**: Single backend web service (layered: web → service → dao / kubernetes)
**Performance Goals**: Read path adds no HuggingFace Hub call — value is served from the persisted column
**Constraints**: No new external integration; reuse HF metadata already fetched (`pipeline_tag`, `config.json` architectures). Detection failures at create/update fail the operation (unchanged behaviour). `ddl-auto: validate` — Flyway owns schema.
**Scale/Scope**: HuggingFace-sourced inference deployments only; one new enum value, one column, one DTO field

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|---|---|---|
| I. Strict layered architecture | PASS | Detection + persistence stay in `service`/`dao`; DTO mapping in `web`. No new cross-layer calls. HF access already encapsulated behind `HuggingFaceClient`. |
| II. Transactional discipline | PASS | Persistence runs inside existing `@Transactional` create/update in `DeploymentService`; no `@Transactional` added to controllers. |
| III. Kubernetes isolation | PASS | No new K8s calls. `prepareServiceSpec` manifest path unchanged. |
| IV. Observability first | PASS | New/changed components keep `@LogExecution`; no new component classes expected beyond reuse. |
| V. Security by configuration | PASS | No auth/secret surface change. Field is read-only (FR-008). |
| Naming conventions | PASS | Reuse `InferenceTask`, `InferenceDeploymentDto`, `*DtoMapper`, `*Entity`. |
| Multi-vendor migration pattern | PASS | New `V1.59` migration authored for H2, POSTGRES, MS_SQL_SERVER; `generateDbSchema` task included. |
| API conventions | PASS | No new endpoint; existing GET/list responses gain a documented field. `@Operation`/`ErrorView` unaffected. |
| Anti-patterns | PASS | No business logic in entities, no generic catch, no polling, `ddl-auto` stays `validate`. |

**Result**: PASS — no violations. Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/024-model-serving-capability/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── inference-deployment-capability.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── model/deployment/
│   ├── InferenceTask.java              # + TEXT_GENERATION enum value
│   └── InferenceDeployment.java        # + inferenceTask field (domain model)
├── service/
│   ├── detection/
│   │   ├── InferenceTaskDetector.java          # + text-generation detection + precedence
│   │   └── InferenceTaskDetectionResult.java   # + textGeneration() factory
│   └── deployment/
│       ├── DeploymentManager.java              # + default no-op enrich hook (interface)
│       ├── InferenceDeploymentManager.java     # override hook: detect + set inferenceTask
│       └── DeploymentService.java              # call enrich hook in create/update before save
├── dao/
│   ├── entity/deployment/InferenceDeploymentEntity.java   # + inferenceTask column (@Enumerated STRING)
│   └── mapper/PersistenceDeploymentMapper.java            # map inferenceTask domain↔entity
└── web/
    ├── dto/deployment/InferenceDeploymentDto.java         # + inferenceTask (read-only)
    └── mapper/DeploymentDtoMapper.java                    # map inferenceTask domain→DTO

src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/
└── V1.59__AddInferenceTaskColumn.sql   # ALTER TABLE inference_deployment ADD inference_task

docs/db-schema.md                       # regenerated via ./gradlew generateDbSchema
```

**Structure Decision**: Single backend service, existing layered layout. Changes are additive and
ride existing inference-deployment classes; the only structural addition is one Flyway migration per
vendor and a default interface hook for create/update enrichment.

## Complexity Tracking

> No constitution violations — section intentionally empty.
