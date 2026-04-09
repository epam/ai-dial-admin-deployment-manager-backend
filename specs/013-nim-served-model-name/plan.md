# Implementation Plan: NIM Served Model Name Override

**Branch**: `013-nim-served-model-name` | **Date**: 2026-04-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/013-nim-served-model-name/spec.md`

## Summary

Auto-inject `NIM_SERVED_MODEL_NAME` environment variable into NIM deployment manifests when not explicitly provided by the user, defaulting to the deployment identifier. This mirrors the existing inference deployment pattern where `--model_name` is auto-injected into args. The change is confined to the manifest generation layer — no new API fields, database columns, or migrations required.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2, NVIDIA NIM CRD (NIMService), MapStruct 1.6.0, Lombok 8.10
**Storage**: N/A (no schema changes)
**Testing**: JUnit 5, Mockito, AssertJ, JSONAssert; `./gradlew testFast` for development
**Target Platform**: Linux server (Spring Boot backend)
**Project Type**: Web service (REST API + Kubernetes operator)
**Performance Goals**: N/A (trivial string operation at manifest generation time)
**Constraints**: Must not break existing NIM deployments; must follow constitution patterns
**Scale/Scope**: Single method addition + tests; ~50 LOC production, ~80 LOC test

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|------|--------|-------|
| Strict Layered Architecture | PASS | Change is in `service/manifest/` — no cross-layer violations |
| Transactional Discipline | PASS | No `@Transactional` involved |
| Kubernetes Isolation | PASS | NimManifestGenerator is in the service layer but only builds manifests (data structures), not K8s API calls |
| Observability First | PASS | NimManifestGenerator already has `@LogExecution`; log statement for skip case follows inference pattern |
| Security by Configuration | PASS | No secrets or auth changes |
| Naming Conventions | PASS | No new classes; method follows existing `setModelNameIfNotSet` naming from inference |
| Code Style | PASS | Will run checkstyle verification |
| Testing Conventions | PASS | Unit tests for NimManifestGenerator follow existing pattern; `shouldDoX` / `shouldFailDoX_whenY` naming |
| Configuration property defaults | PASS | No new configuration properties |
| Configuration documentation | PASS | No new env vars in application.yml |

## Project Structure

### Documentation (this feature)

```text
specs/013-nim-served-model-name/
├── spec.md
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (empty — no API changes)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
└── service/manifest/
    └── NimManifestGenerator.java          # Add setServedModelNameIfNotSet() method

src/test/java/com/epam/aidial/deployment/manager/
└── service/manifest/
    └── NimManifestGeneratorTest.java      # Add 3 test cases for served model name logic
```

**Structure Decision**: No new files or packages. All changes are additions to existing `NimManifestGenerator` (production) and `NimManifestGeneratorTest` (test).

## Complexity Tracking

No constitution violations — table not needed.

## Design

### Approach

Mirror the `InferenceManifestGenerator.setModelNameIfNotSet()` pattern, adapted for NIM's environment-variable-based mechanism:

1. **New constant**: `NIM_SERVED_MODEL_NAME_ENV = "NIM_SERVED_MODEL_NAME"` in `NimManifestGenerator`.

2. **New private method**: `setServedModelNameIfNotSet(String deploymentName, List<SimpleEnvVar> simpleEnvs, List<SensitiveEnvVar> sensitiveEnvs, ListMapper<Env> envListMapper)` that:
   - Checks if `NIM_SERVED_MODEL_NAME` is already present in `simpleEnvs` OR `sensitiveEnvs` (by name)
   - If present: log info and return (respect explicit override)
   - If absent: add `NIM_SERVED_MODEL_NAME=<deploymentName>` to the env list via `envListMapper`

3. **Call site**: In `serviceConfig()`, invoke `setServedModelNameIfNotSet()` after `applySimpleEnvs()` and `applySensitiveEnvs()` (line ~78 in current code), so user-provided envs are already applied and the default is appended only if missing.

### Reference: Inference Pattern

```java
// InferenceManifestGenerator.java:125-133
private void setModelNameIfNotSet(String modelName, MappingChain<Model> modelChain) {
    var command = modelChain.getNullable(InferenceMappers.MODEL_COMMAND_FIELD).data();
    var args = modelChain.getNullable(InferenceMappers.MODEL_ARGS_FIELD).data();
    if (isArgPresent(MODEL_NAME_ARGUMENT_NAME, command) || isArgPresent(MODEL_NAME_ARGUMENT_NAME, args)) {
        log.info("Argument {} is already set for model '{}', skipping.", MODEL_NAME_ARGUMENT_NAME, modelName);
        return;
    }
    modelChain.get(InferenceMappers.MODEL_ARGS_FIELD).data().addAll(List.of(MODEL_NAME_ARGUMENT_NAME, modelName));
}
```

### NIM Adaptation

For NIM, the check is simpler — just look for the env var name in the two input lists (no `=` form or positional args to worry about):

```java
private void setServedModelNameIfNotSet(String deploymentName,
                                         List<SimpleEnvVar> simpleEnvs,
                                         List<SensitiveEnvVar> sensitiveEnvs,
                                         ListMapper<Env> envListMapper) {
    boolean alreadySet = simpleEnvs.stream().anyMatch(e -> NIM_SERVED_MODEL_NAME_ENV.equals(e.getName()))
            || sensitiveEnvs.stream().anyMatch(e -> NIM_SERVED_MODEL_NAME_ENV.equals(e.getName()));
    if (alreadySet) {
        log.info("Environment variable {} is already set for NIM deployment '{}', skipping.",
                NIM_SERVED_MODEL_NAME_ENV, deploymentName);
        return;
    }
    var envVarChain = envListMapper.get(NIM_SERVED_MODEL_NAME_ENV);
    envVarChain.data().setValue(deploymentName);
}
```

### Test Cases

| Test | Input | Expected |
|------|-------|----------|
| Default model name injected | No `NIM_SERVED_MODEL_NAME` in envs | Env var added with deployment name as value |
| Explicit simple env preserved | `NIM_SERVED_MODEL_NAME` in simple envs | User value preserved, no duplicate added |
| Explicit sensitive env preserved | `NIM_SERVED_MODEL_NAME` in sensitive envs | User value preserved, no duplicate added |
