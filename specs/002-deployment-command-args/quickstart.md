# Quickstart: Command and Args for All Deployment Types

**Date**: 2026-03-10
**Feature**: [spec.md](spec.md)

## Implementation Order

### Step 1: Database Migration (V1.49)
Create migration files for all 3 vendors to move `command`/`args` from `inference_deployment` to `deployment` table.

**Files**: `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/V1.49__MoveCommandArgsToDeploymentTable.sql`

### Step 2: Entity Layer
1. Add `command` and `args` (List\<String\>, `@JdbcTypeCode(SqlTypes.JSON)`) to `DeploymentEntity`
2. Remove `command` and `args` from `InferenceDeploymentEntity`

### Step 3: Domain Model Layer
1. Add `command` and `args` (List\<String\>) to `Deployment` and `CreateDeployment`
2. Remove `command` and `args` from `InferenceDeployment` and `CreateInferenceDeployment`

### Step 4: DTO Layer
1. Add `command` and `args` (String, `@Nullable`) to `CreateDeploymentRequestDto` and `DeploymentDto`
2. Remove `command` and `args` from `CreateInferenceDeploymentRequestDto` and `InferenceDeploymentDto`

### Step 5: Mapper Updates
1. Update `PersistenceDeploymentMapper.updateEntityFromDomain()` — move command/args handling from Inference-specific block to base block
2. Verify `DeploymentDtoMapper` auto-discovers `stringToList`/`listToString` for base-level fields (MapStruct should handle this automatically)
3. Verify `DeploymentMapper` (service-level) needs no changes (fields are inherited)

### Step 6: Manifest Generator Updates
1. Add `@Nullable List<String> command, @Nullable List<String> args` params to `KnativeManifestGenerator.serviceConfig()`
2. Add `@Nullable List<String> command, @Nullable List<String> args` params to `NimManifestGenerator.serviceConfig()`
3. Apply command/args to container spec in both generators (null-check guard pattern)
4. Update all callers of these methods to pass `deployment.getCommand()`, `deployment.getArgs()`

### Step 7: Tests
1. Update `KnativeManifestGeneratorTest` — add command/args test cases
2. Update `NimManifestGeneratorTest` — add command/args test cases
3. Update functional tests for MCP, Adapter, Interceptor, NIM deployments — CRUD with command/args
4. Verify existing Inference tests still pass (backward compatibility)

## Verify

```bash
./gradlew checkstyleMain checkstyleTest
./gradlew clean build
```

## Key References

| What | Where |
|------|-------|
| Existing command/args pattern | `InferenceManifestGenerator.java:76-84` |
| String↔List conversion | `DeploymentDtoMapper.java:260-282` |
| CommandLineUtils parser | `web/utils/CommandLineUtils.java` |
| Persistence update pattern | `PersistenceDeploymentMapper.java:128-148` |
| Latest migration | `V1.48__CreateDeploymentTopicsTable.sql` |
