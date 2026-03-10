# Research: Support Command and Args for All Deployment Types

**Date**: 2026-03-10
**Feature**: [spec.md](spec.md)

## R1: Field Consolidation Strategy — Moving Fields from Subtype to Base

**Decision**: Add `command` and `args` fields to the base `Deployment`, `CreateDeployment`, `DeploymentEntity`, `CreateDeploymentRequestDto`, and `DeploymentDto` classes. Remove the same fields from all Inference-specific classes.

**Rationale**: The base-level approach ensures all deployment types automatically inherit command/args support. Since the decision was made during clarification (Q1, Q4, Q5) to consolidate at the base level, this is the cleanest approach — single source of truth, no duplicate fields, no conditional mapping logic.

**Alternatives considered**:
- Keep Inference-specific fields and add to image-based types only → rejected (duplication, inconsistent API surface)
- Add to each subtype individually → rejected (violates DRY, complicates MapStruct mappings)

**Key files affected**:
- `Deployment.java` (line ~53): Add after `topics` field
- `CreateDeployment.java` (line ~35): Add after `topics` field
- `DeploymentEntity.java` (line ~104): Add with `@JdbcTypeCode(SqlTypes.JSON)` annotation
- `CreateDeploymentRequestDto.java` (line ~72): Add `@Nullable String command, args`
- `DeploymentDto.java` (line ~66): Add `@Nullable String command, args`
- `InferenceDeployment.java`: Remove `command`, `args` fields
- `CreateInferenceDeployment.java`: Remove `command`, `args` fields
- `InferenceDeploymentEntity.java` (lines 35-38): Remove `command`, `args` fields
- `CreateInferenceDeploymentRequestDto.java` (lines 22-27): Remove `command`, `args` fields
- `InferenceDeploymentDto.java` (lines 22-25): Remove `command`, `args` fields

## R2: Database Migration Strategy

**Decision**: Create a single Flyway migration `V1.49__MoveCommandArgsToDeploymentTable.sql` for each vendor that:
1. Adds `command` and `args` JSONB/JSON columns to the `deployment` table (nullable)
2. Copies existing data from `inference_deployment.command` and `inference_deployment.args` to the corresponding rows in `deployment`
3. Drops the `command` and `args` columns from `inference_deployment`

**Rationale**: Single migration keeps the change atomic. Data is preserved via UPDATE...FROM join. The JSONB type matches the existing Inference pattern (list of strings serialized as JSON array).

**Alternatives considered**:
- Two separate migrations (add + drop) → rejected (unnecessary complexity for a simple column move)
- Java-based migration → rejected (SQL is sufficient; no complex data transformation needed)

**Vendor-specific SQL notes**:
- **PostgreSQL**: `ALTER TABLE deployment ADD COLUMN command JSONB; UPDATE deployment SET command = i.command FROM inference_deployment i WHERE deployment.id = i.id; ALTER TABLE inference_deployment DROP COLUMN command;` (same for `args`)
- **H2**: Same syntax (H2 supports JSONB since v2.x)
- **SQL Server**: Use `NVARCHAR(MAX)` instead of JSONB; JSON stored as string

## R3: MapStruct Mapping Changes

**Decision**: Move `stringToList`/`listToString` conversion to the base mapping level in `DeploymentDtoMapper`. MapStruct will auto-detect these methods for String↔List<String> conversion on the base class fields.

**Rationale**: The `stringToList()` and `listToString()` methods already exist as `protected` methods in `DeploymentDtoMapper.java` (lines 260-282). When `command` and `args` move from Inference-specific DTOs/models to the base level, MapStruct will automatically apply these conversion methods since the field types match (String in DTO ↔ List<String> in domain model).

**Key insight**: No explicit `@Mapping` annotations are needed for the conversion — MapStruct discovers compatible protected methods automatically. The existing methods handle:
- Parsing via `CommandLineUtils.parseCommandline()` (shell-like tokenization)
- Quoting via `CommandLineUtils.quoteArgument()` (round-trip preservation)
- Error handling with descriptive `IllegalArgumentException`

## R4: PersistenceDeploymentMapper Changes

**Decision**: Move command/args update logic from the Inference-specific `instanceof` block (lines 142-148) to the base `updateEntityFromDomain()` method.

**Rationale**: Since `command` and `args` are now on `DeploymentEntity` (base), the mapping should happen at the base level alongside other base fields like `displayName`, `scaling`, etc. The existing Inference-specific block will no longer need to handle these fields.

**Key changes**:
- Add `existingEntity.setCommand(updatedEntity.getCommand())` and `existingEntity.setArgs(updatedEntity.getArgs())` in the base update block
- Remove `existingInference.setCommand(...)` and `existingInference.setArgs(...)` from the Inference-specific block (lines 146-147)

## R5: Manifest Generator Changes

**Decision**: Add `@Nullable List<String> command` and `@Nullable List<String> args` parameters to `KnativeManifestGenerator.serviceConfig()` and `NimManifestGenerator.serviceConfig()`. Apply them to the container spec when non-null.

**Rationale**: Follows the exact pattern used by `InferenceManifestGenerator` (lines 76-84). The Knative and NIM generators create containers but currently have no way to override the entrypoint. Adding these parameters with null-check guards is minimal and consistent.

**Implementation pattern** (from InferenceManifestGenerator lines 76-84):
```java
if (command != null) {
    container.setCommand(command);
}
if (args != null) {
    container.setArgs(args);
}
```

**Callers to update**:
- Services that call `KnativeManifestGenerator.serviceConfig()` (MCP, Adapter, Interceptor deployment services)
- Services that call `NimManifestGenerator.serviceConfig()` (NIM deployment service)
- Each caller must pass `deployment.getCommand()` and `deployment.getArgs()` from the domain model

## R6: Inference --model_name Auto-Injection Preservation

**Decision**: Keep the `--model_name` auto-injection logic in `InferenceManifestGenerator` unchanged. This behavior is Inference-specific and will NOT be replicated in other generators.

**Rationale**: Per clarification Q3, `--model_name` is valid only for Inference deployments. The auto-injection logic (lines 86-134 of `InferenceManifestGenerator.java`) checks if `--model_name` is already present in args and adds it if missing. This remains on the Inference generator; `command` and `args` values are passed to it from `Deployment.getCommand()` / `Deployment.getArgs()` (base level) instead of `InferenceDeployment.getCommand()` / `InferenceDeployment.getArgs()`.
