# Implementation Plan: NIM KServe Migration

**Branch**: `019-nim-kserve-migration` | **Date**: 2026-04-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/014-nim-kserve-migration/spec.md`

## Summary

Migrate NIM CRD generation from `standalone` to `kserve` inference platform. This involves: (1) setting `inferencePlatform: kserve` in the NIM service template, (2) adding Knative autoscaling annotations (class, metric, target, min-scale, max-scale, initial-scale) using the deployment's `Scaling` model, (3) removing nginx ingress generation in favor of Knative's router, (4) auto-injecting `NIM_CACHE_PATH=/tmp` env var, and (5) making autoscaling defaults configurable via application properties.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.10, Gradle 8.13
**Primary Dependencies**: Fabric8 Kubernetes Client 7.5.2, NVIDIA NIM CRD (NIMService), MapStruct 1.6.0, Lombok 8.10
**Storage**: N/A (no database changes — manifest generation only)
**Testing**: JUnit 5 + Mockito (unit tests), H2 functional tests via `./gradlew testFast`
**Target Platform**: Linux server (Spring Boot web service)
**Project Type**: Web service (Kubernetes deployment manager)
**Performance Goals**: N/A (manifest generation is not performance-critical)
**Constraints**: Google Java Style (180-char lines), `-Werror`, Checkstyle 10.21.4
**Scale/Scope**: ~9 files modified, ~3 new configuration properties

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Status | Notes |
|---|---|---|
| Strict layered architecture | PASS | Changes are in `service/manifest/` (service layer) and `kubernetes/knative/` (kubernetes layer). No cross-layer violations. |
| Kubernetes isolation | PASS | All K8s manifest generation stays in `service/manifest/` and `kubernetes/` packages. |
| `@LogExecution` on components | PASS | `NimManifestGenerator` already has `@LogExecution`. |
| No business logic in entities | PASS | No entity changes. |
| Configuration defaults in application.yml only | PASS | New properties will have defaults in `application.yml` via `${ENV_VAR:default}` syntax. |
| Checkstyle (Google Java Style, 180-char) | PASS | Will verify with `./gradlew checkstyleMain checkstyleTest`. |
| `-Werror` compilation | PASS | Removing dead code (ingress methods) avoids unused import warnings. |
| docs/configuration.md update | REQUIRED | New env vars must be documented. |

No constitution violations. All gates pass.

## Project Structure

### Documentation (this feature)

```text
specs/014-nim-kserve-migration/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0: research decisions
├── data-model.md        # Phase 1: data model changes
└── quickstart.md        # Phase 1: quickstart guide
```

### Source Code (repository root)

```text
src/main/java/com/epam/aidial/deployment/manager/
├── kubernetes/knative/
│   └── KnativeAnnotations.java              # Add AUTOSCALING_CLASS, AUTOSCALING_METRIC constants
├── configuration/
│   └── NimDeployProperties.java             # Add autoscaling config fields
├── service/manifest/
│   └── NimManifestGenerator.java            # Add scaling, remove ingress, add NIM_CACHE_PATH, add router
├── service/deployment/
│   └── NimDeploymentManager.java            # Pass scaling, remove useExternalUrl/clusterHost
└── utils/mapping/
    └── NimMappers.java                      # Add EXPOSE_ROUTER_FIELD mapper (if needed)

src/main/resources/
└── application.yml                          # Update nim-service-config template, add autoscaling props

src/test/java/com/epam/aidial/deployment/manager/
├── service/manifest/
│   └── NimManifestGeneratorTest.java        # Update for new signature, add scaling tests
└── service/deployment/
    └── NimDeploymentManagerTest.java        # Update for removed ingress, new scaling param

src/test/resources/manifest/
├── nim_service_template.json                # Update: add inferencePlatform, router
└── nim_service_with_*.json                  # Update expected outputs

docs/
└── configuration.md                         # Document new NIM autoscaling env vars
```

**Structure Decision**: All changes fit within the existing layered architecture. No new packages or structural changes needed.

## Implementation Steps

### Step 1: Add Knative annotation constants

**File**: `src/main/java/com/epam/aidial/deployment/manager/kubernetes/knative/KnativeAnnotations.java`

Add two new constants:
```java
public static final String AUTOSCALING_CLASS = "autoscaling.knative.dev/class";
public static final String AUTOSCALING_METRIC = "autoscaling.knative.dev/metric";
```

### Step 2: Add autoscaling configuration properties

**File**: `src/main/java/com/epam/aidial/deployment/manager/configuration/NimDeployProperties.java`

Add three new fields (defaults in application.yml):
- `private String autoscalingClass;`
- `private String autoscalingMetric;`
- `private int autoscalingTarget;`

**File**: `src/main/resources/application.yml`

Under `app.nim.deploy`, add:
```yaml
autoscaling-class: ${K8S_NIM_AUTOSCALING_CLASS:kpa.autoscaling.knative.dev}
autoscaling-metric: ${K8S_NIM_AUTOSCALING_METRIC:concurrency}
autoscaling-target: ${K8S_NIM_AUTOSCALING_TARGET:10}
```

### Step 3: Update NIM service config template

**File**: `src/main/resources/application.yml`

In `app.nim-service-config.spec`:
- Add `inferencePlatform: kserve`
- Add `expose.router: {}` (empty object to signal kserve routing)
- Keep existing `expose.service` section

The `nim-service-expose-ingress-config` section can remain in YAML for backward compatibility but will no longer be referenced from code.

### Step 4: Modify NimManifestGenerator

**File**: `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`

Changes:
1. **Add `NimDeployProperties` dependency** to constructor (for autoscaling config)
2. **Add `Scaling` parameter** to `serviceConfig()` method signature
3. **Remove parameters**: `useExternalUrl`, `clusterHost`
4. **Add `applyScaling()` method** following InferenceService pattern:
   - If `scaling != null`:
     - `annotations.put(INITIAL_SCALE, String.valueOf(Math.max(scaling.getMinReplicas(), 1)))`
     - `annotations.put(MIN_SCALE, String.valueOf(scaling.getMinReplicas()))`
     - `annotations.put(MAX_SCALE, String.valueOf(scaling.getMaxReplicas()))`
   - Always set (from config):
     - `annotations.put(AUTOSCALING_CLASS, nimDeployProperties.getAutoscalingClass())`
     - `annotations.put(AUTOSCALING_METRIC, nimDeployProperties.getAutoscalingMetric())`
     - `annotations.put(AUTOSCALING_TARGET, String.valueOf(nimDeployProperties.getAutoscalingTarget()))`
   - If `scaling.getScaleToZeroDelaySeconds() != null`:
     - `annotations.put(SCALE_TO_ZERO_RETENTION, delay + "s")`
5. **Add `NIM_CACHE_PATH` auto-injection** following `setServedModelNameIfNotSet` pattern:
   - New constant: `NIM_CACHE_PATH_ENV = "NIM_CACHE_PATH"`
   - New constant: `NIM_CACHE_PATH_VALUE = "/tmp"`
   - New method: `setCachePathIfNotSet()` — sets `NIM_CACHE_PATH=/tmp` if not already in env vars
6. **Remove ingress code**:
   - Remove the `if (useExternalUrl)` block and `applyExposeIngress()` call
   - Remove `applyExposeIngress()`, `buildTls()`, `buildRule()` methods
   - Remove unused ingress-related imports
7. **Add router setup**: Set `expose.router` to new empty `Router()` object
8. **Call `applyScaling()`** in `serviceConfig()` after progress deadline

### Step 5: Modify NimDeploymentManager

**File**: `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java`

Changes to `prepareServiceSpec()`:
1. Remove `var useExternalUrl = !nimDeployProperties.isUseClusterInternalUrl();`
2. Pass `deployment.getScaling()` to `nimManifestGenerator.serviceConfig()`
3. Remove `useExternalUrl` and `nimDeployProperties.getClusterHost()` args from the call

### Step 6: Update test resource templates

**File**: `src/test/resources/manifest/nim_service_template.json`

Update to include:
- `"inferencePlatform": "kserve"`
- `"expose": { "service": {...}, "router": {} }` (no ingress)

Update expected output JSONs (`nim_service_with_envs.json`, `nim_service_with_resources.json`, etc.) to match new structure.

### Step 7: Update NimManifestGeneratorTest

**File**: `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`

- Update all test method calls to match new `serviceConfig()` signature (remove `useExternalUrl`/`clusterHost`, add `scaling`)
- Remove ingress-related tests (`testServiceConfig_withExternalUrlAndClusterHost_setsExposeIngress`, etc.)
- Add new tests:
  - `shouldSetAutoscalingAnnotations_whenScalingProvided()` — verify all 6 annotations
  - `shouldSetInitialScaleToOne_whenMinReplicasIsZero()` — verify `Math.max(0, 1) = 1`
  - `shouldSetAutoscalingConfigAnnotations_whenNoScalingProvided()` — verify class/metric/target from config
  - `shouldSetNimCachePath_whenNotProvidedByUser()` — verify NIM_CACHE_PATH auto-injection
  - `shouldNotOverrideNimCachePath_whenProvidedByUser()` — verify user override
  - `shouldSetExploseRouter()` — verify `expose.router` is set
  - `shouldNotSetExposeIngress()` — verify no ingress section

### Step 8: Update NimDeploymentManagerTest

**File**: `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java`

- Update mock invocations for `nimManifestGenerator.serviceConfig()` to match new signature
- Remove tests for external URL / ingress behavior
- Add test for scaling parameter passing

### Step 9: Update configuration documentation

**File**: `docs/configuration.md`

Add new NIM autoscaling properties:
- `K8S_NIM_AUTOSCALING_CLASS` — default: `kpa.autoscaling.knative.dev`
- `K8S_NIM_AUTOSCALING_METRIC` — default: `concurrency`
- `K8S_NIM_AUTOSCALING_TARGET` — default: `10`

### Step 10: Verify

```bash
./gradlew checkstyleMain checkstyleTest
./gradlew testFast
```

## Complexity Tracking

No constitution violations to justify.
