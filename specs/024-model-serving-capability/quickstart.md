# Quickstart: Model Serving Capability API

## What this delivers

A `inferenceTask` field on inference deployment responses telling the frontend what the model exposes:
`TEXT_GENERATION` (chat completion), `TEXT_CLASSIFICATION` (MCP toolset), or `NONE`.

## Try it locally

1. Run the service:
   ```bash
   ./gradlew bootRun
   ```
2. Create an inference deployment from a known text-generation HuggingFace model (e.g. a causal-LM).
3. Fetch it:
   ```bash
   curl -s http://localhost:8080/api/v1/deployments/<id> | jq '.inferenceTask'
   # → "TEXT_GENERATION"
   ```
4. Create one from a sequence-classification model → `inferenceTask` is `"TEXT_CLASSIFICATION"`.
5. Create one from an unrelated model → `"NONE"`.
6. Change a deployment's model source to a different task → re-fetch shows the updated value.

## Verification gates

```bash
./gradlew testFast                         # H2 fast suite
./gradlew checkstyleMain checkstyleTest     # style
./gradlew generateDbSchema                  # after the V1.59 migration; commit docs/db-schema.md
./gradlew clean build                       # full gate before PR
```

## Key acceptance checks (from spec)

- US1: inference deployment response carries `inferenceTask`; correct for generation / classification / none; present on list items.
- US2: detector returns `TEXT_GENERATION` for generation models; classification & none outcomes unchanged.
- US3: changing the model source updates the persisted value.
- FR-009: text-classification / none manifest generation (transformer chaining) is byte-for-byte unchanged.

## Pointers

- Detection: `service/detection/InferenceTaskDetector.java`
- Enum: `model/deployment/InferenceTask.java`
- Persist hook: `service/deployment/InferenceDeploymentManager.java` + `DeploymentService` create/update
- Migration: `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/V1.59__AddInferenceTaskColumn.sql`
- API field: `web/dto/deployment/InferenceDeploymentDto.java` + `web/mapper/DeploymentDtoMapper.java`
- Spec to update on implement: `specs/inference-deployments/spec.md`
