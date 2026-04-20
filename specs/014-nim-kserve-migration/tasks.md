# Tasks: NIM KServe Migration

**Input**: Design documents from `specs/014-nim-kserve-migration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Tests**: Test update tasks are included because this feature modifies existing test-covered code (NimManifestGeneratorTest, NimDeploymentManagerTest). Existing tests must be updated to match the new signatures and behavior.

**Organization**: Tasks are grouped by user story. US1 and US2 (both P1) are combined into a single phase because they are architecturally inseparable — switching to kserve requires removing ingress, and vice versa.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Blocking Prerequisites)

**Purpose**: Add constants, configuration properties, and template changes that all user stories depend on

**CRITICAL**: No user story work can begin until this phase is complete

- [x] T001 [P] Add `AUTOSCALING_CLASS` and `AUTOSCALING_METRIC` string constants to `src/main/java/com/epam/aidial/deployment/manager/kubernetes/knative/KnativeAnnotations.java` — values: `autoscaling.knative.dev/class` and `autoscaling.knative.dev/metric`
- [x] T002 [P] Add autoscaling fields (`autoscalingClass`, `autoscalingMetric`, `autoscalingTarget`) to `src/main/java/com/epam/aidial/deployment/manager/configuration/NimDeployProperties.java` — no field initializers (defaults in application.yml per constitution)
- [x] T003 Update `src/main/resources/application.yml`: (a) add `autoscaling-class`, `autoscaling-metric`, `autoscaling-target` under `app.nim.deploy` with env var defaults `${K8S_NIM_AUTOSCALING_CLASS:kpa.autoscaling.knative.dev}`, `${K8S_NIM_AUTOSCALING_METRIC:concurrency}`, `${K8S_NIM_AUTOSCALING_TARGET:10}`; (b) add `inferencePlatform: kserve` to `app.nim-service-config.spec`; (c) add `router: {}` under `app.nim-service-config.spec.expose`
- [x] T004 Update test template `src/test/resources/manifest/nim_service_template.json`: add `"inferencePlatform": "kserve"`, add `"router": {}` to the `expose` object

**Checkpoint**: Foundation ready — constants, config, and templates are in place for user story implementation

---

## Phase 2: User Story 1+2 — KServe Platform Switch & Ingress Removal (Priority: P1) MVP

**Goal**: Switch NIMService manifest generation from `standalone` with nginx ingress to `kserve` with Knative autoscaling annotations. Remove all ingress generation. This is the core architectural change.

**Independent Test**: Deploy a NIM model and verify the generated NIMService manifest has `inferencePlatform: kserve`, contains all six Knative autoscaling annotations (class, metric, target, min-scale, max-scale, initial-scale), has no `expose.ingress` section, and has `expose.router` + `expose.service`.

### Implementation

- [x] T005 [US1] Refactor `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`: (a) add `NimDeployProperties` constructor parameter; (b) change `serviceConfig()` signature — add `@Nullable Scaling scaling` parameter, remove `boolean useExternalUrl` and `@Nullable String clusterHost` parameters; (c) remove the `if (useExternalUrl)` block calling `applyExposeIngress`; (d) add `exposeChain.data().setRouter(new Router())` after the `applyExposeService` call for kserve routing; (e) remove methods: `applyExposeIngress()`, `buildTls()`, `buildRule()`; (f) remove unused imports for ingress-related classes (`Rules`, `Tls`, `Http`, `Paths`, `Backend`, `Service` from expose.ingress packages, `Port`)
- [x] T006 [US1] Add `applyScaling()` method to `NimManifestGenerator` following `InferenceManifestGenerator.applyScaling()` pattern: (a) always set annotation `AUTOSCALING_CLASS` from `nimDeployProperties.getAutoscalingClass()`; (b) always set `AUTOSCALING_METRIC` from `nimDeployProperties.getAutoscalingMetric()`; (c) always set `AUTOSCALING_TARGET` from `String.valueOf(nimDeployProperties.getAutoscalingTarget())`; (d) if `scaling != null`: set `INITIAL_SCALE` = `String.valueOf(Math.max(scaling.getMinReplicas(), 1))`, `MIN_SCALE` = `String.valueOf(scaling.getMinReplicas())`, `MAX_SCALE` = `String.valueOf(scaling.getMaxReplicas())`; (e) if `scaling != null && scaling.getScaleToZeroDelaySeconds() != null`: set `SCALE_TO_ZERO_RETENTION` = `delay + "s"`; (f) call `applyScaling()` in `serviceConfig()` after `applyProgressDeadline`
- [x] T007 [US2] Update `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java` `prepareServiceSpec()`: (a) remove `var useExternalUrl = !nimDeployProperties.isUseClusterInternalUrl();`; (b) update `nimManifestGenerator.serviceConfig()` call — add `deployment.getScaling()` argument, remove `useExternalUrl` and `nimDeployProperties.getClusterHost()` arguments
- [x] T008 [US1] Update `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`: (a) add `NimDeployProperties` mock to test setup; (b) update all `serviceConfig()` calls to match new signature (remove useExternalUrl/clusterHost, add scaling=null for existing tests); (c) remove ingress-related tests (`testServiceConfig_withExternalUrlAndClusterHost_setsExposeIngress`, `testServiceConfig_withExternalUrl_setsIngressAnnotationsFromConfig`, and validation test for blank cluster host); (d) add test `shouldSetAutoscalingAnnotations_whenScalingProvided` — verify all 6 annotations (class, metric, target, min-scale, max-scale, initial-scale); (e) add test `shouldSetInitialScaleToOne_whenMinReplicasIsZero` — verify `Math.max(0, 1) = 1`; (f) add test `shouldSetAutoscalingConfigAnnotations_whenNoScalingProvided` — verify class/metric/target from config even with null scaling; (g) add test `shouldSetExposeRouter` — verify `expose.router` is set; (h) add test `shouldNotSetExposeIngress` — verify no ingress section in output
- [x] T009 [US2] Update `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java`: (a) update mock invocations for `nimManifestGenerator.serviceConfig()` to match new signature; (b) remove test `deploy_shouldThrowWhenExternalUrlRequestedAndClusterHostBlank`; (c) remove test `deploy_shouldInvokeGeneratorWithUseExternalUrlAndClusterHostWhenExternalUrlRequested`; (d) add/update test verifying `deployment.getScaling()` is passed to generator
- [x] T010 [P] [US1] Update expected test JSON fixtures in `src/test/resources/manifest/`: update `nim_service_with_envs.json` and `nim_service_with_resources.json` to remove ingress section, add `"router": {}` to expose, add `"inferencePlatform": "kserve"`

**Checkpoint**: US1+US2 complete — NIMService manifests use kserve with autoscaling annotations, no ingress. Run `./gradlew testFast` to verify.

---

## Phase 3: User Story 3 — NIM_CACHE_PATH Environment Variable (Priority: P2)

**Goal**: Automatically inject `NIM_CACHE_PATH=/tmp` env var on every NIM deployment for correct cache behavior under kserve mode.

**Independent Test**: Deploy a NIM model without setting NIM_CACHE_PATH and verify the generated manifest includes `NIM_CACHE_PATH=/tmp`. Deploy with explicit NIM_CACHE_PATH and verify the user's value is preserved.

### Implementation

- [x] T011 [US3] Add NIM_CACHE_PATH auto-injection in `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`: (a) add constant `NIM_CACHE_PATH_ENV = "NIM_CACHE_PATH"`; (b) add constant `NIM_CACHE_PATH_VALUE = "/tmp"`; (c) add method `setCachePathIfNotSet()` following the same pattern as `setServedModelNameIfNotSet()` — check if NIM_CACHE_PATH is already set in simple or sensitive env vars, if not set it to `/tmp`; (d) call `setCachePathIfNotSet()` in `serviceConfig()` after `setServedModelNameIfNotSet()`
- [x] T012 [US3] Add NIM_CACHE_PATH tests in `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`: (a) add test `shouldSetNimCachePath_whenNotProvidedByUser` — verify NIM_CACHE_PATH=/tmp is in env vars; (b) add test `shouldNotOverrideNimCachePath_whenProvidedByUser` — verify user-provided value is preserved

**Checkpoint**: US3 complete — NIM_CACHE_PATH is auto-injected. Run `./gradlew testFast` to verify.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and final validation

- [x] T013 Update `docs/configuration.md` with new NIM autoscaling environment variables: `K8S_NIM_AUTOSCALING_CLASS` (default: `kpa.autoscaling.knative.dev`), `K8S_NIM_AUTOSCALING_METRIC` (default: `concurrency`), `K8S_NIM_AUTOSCALING_TARGET` (default: `10`)
- [x] T014 Run `./gradlew checkstyleMain checkstyleTest` and fix any style violations
- [x] T015 Run `./gradlew testFast` and verify all tests pass

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No dependencies — can start immediately
- **US1+US2 (Phase 2)**: Depends on Foundational completion — BLOCKS US3
- **US3 (Phase 3)**: Depends on Phase 2 (NimManifestGenerator signature must be finalized first)
- **Polish (Phase 4)**: Depends on all user stories being complete

### User Story Dependencies

- **US1+US2 (P1)**: Can start after Foundational — no dependencies on other stories
- **US3 (P2)**: Can start after US1+US2 — adds to the same file (NimManifestGenerator) so sequential to avoid conflicts
- **US4 (P3)**: Fully implemented within Foundational (T002, T003) + US1 (T006) — no separate phase needed. Config properties are wired in foundational, consumed in applyScaling(). Test coverage in T008.

### Within Each Phase

- Tasks marked [P] can run in parallel
- T005 and T006 are sequential (T006 adds a method that T005's refactored serviceConfig() must call)
- T007 depends on T005 (signature change)
- T008 and T009 can run in parallel (different test files)
- T010 can run in parallel with T008/T009 (different files)

### Parallel Opportunities

```
Phase 1:  T001 ──┐
          T002 ──┼── all parallel (different files)
          T003 ──┘
          T004 ──┘

Phase 2:  T005 → T006 → T007 (sequential: same/dependent files)
          T008 ──┐
          T009 ──┼── parallel after T007 (different test files)
          T010 ──┘

Phase 3:  T011 → T012 (sequential: same files)

Phase 4:  T013 → T014 → T015 (sequential: verify after docs)
```

---

## Implementation Strategy

### MVP First (US1+US2 Only)

1. Complete Phase 1: Foundational
2. Complete Phase 2: US1+US2 (kserve + ingress removal + autoscaling)
3. **STOP and VALIDATE**: Run `./gradlew testFast` — NIM deployments now use kserve
4. Deploy/demo if ready

### Incremental Delivery

1. Complete Foundational → Constants, config, templates ready
2. Add US1+US2 → Test independently → Deploy/Demo (MVP!)
3. Add US3 → NIM_CACHE_PATH auto-injection → Test → Deploy/Demo
4. Polish → Documentation, final validation

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US4 (Configurable autoscaling defaults) has no separate phase — it is implemented across Foundational (T002, T003) and US1 (T006, T008) tasks
- US1 and US2 are combined into one phase because switching to kserve and removing ingress are architecturally inseparable
- Commit after each task or logical group
- Stop at any checkpoint to validate independently
