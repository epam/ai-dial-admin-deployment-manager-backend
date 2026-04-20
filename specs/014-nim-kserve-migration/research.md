# Research: NIM KServe Migration

## Decision 1: Scaling annotation pattern for NIM

**Decision**: Follow the InferenceService pattern for `initial-scale` computation (`Math.max(minReplicas, 1)`) and the Knative Service pattern for annotation-based scaling (`min-scale`, `max-scale` as annotations rather than spec fields).

**Rationale**: NIMService does not have a `Predictor` object like InferenceService, so `minReplicas`/`maxReplicas` cannot be set as spec fields. Instead, they must be set as Knative autoscaling annotations (like Knative Service). The `initial-scale` formula (`Math.max(minReplicas, 1)`) is shared between both InferenceService and Knative Service generators.

**Alternatives considered**:
- Using only InferenceService pattern (predictor fields): Not possible — NIMService has no Predictor concept.
- Setting `scale.hpa` on NIMService spec: Contradicts kserve mode which uses Knative autoscaling, not HPA.

## Decision 2: Autoscaling class/metric annotations

**Decision**: Add two new constants to `KnativeAnnotations` for `autoscaling.knative.dev/class` and `autoscaling.knative.dev/metric`. These are new to the codebase — currently not used by InferenceService or Knative Service generators because those rely on Knative defaults. NIM must set them explicitly.

**Rationale**: The user's target manifest explicitly includes `autoscaling.knative.dev/class: kpa.autoscaling.knative.dev` and `autoscaling.knative.dev/metric: concurrency`. These annotations configure the Pod Autoscaler type and metric. The existing InferenceService and Knative generators don't set these because Knative Serving applies KPA+concurrency as defaults. NIM with `inferencePlatform: kserve` needs them explicitly to ensure the NIM operator configures Knative correctly.

**Alternatives considered**:
- Relying on Knative defaults: Not safe — the NIM operator may not inherit Knative defaults the same way.

## Decision 3: NIM_CACHE_PATH auto-injection

**Decision**: Add `NIM_CACHE_PATH=/tmp` as an automatically injected env var in `NimManifestGenerator`, following the same pattern as `NIM_SERVED_MODEL_NAME` auto-injection (set if not already provided by user).

**Rationale**: In kserve mode, the PVC-based cache path may not be available the same way. `/tmp` ensures the NIM container can cache models in an ephemeral location. The env var should be overridable (if user explicitly sets it, their value wins).

**Alternatives considered**:
- Hardcoding in application.yml template: Less flexible, not easily overridable per-deployment.
- Making it configurable via properties: Overkill — `/tmp` is the correct value for kserve mode.

## Decision 4: Ingress removal approach

**Decision**: Remove the `useExternalUrl` and `clusterHost` parameters from `NimManifestGenerator.serviceConfig()`. Remove the `applyExposeIngress()`, `buildTls()`, and `buildRule()` methods entirely. Set `expose.router` to an empty `Router` object.

**Rationale**: The `expose.ingress` field on `NIMServiceSpec.Expose` is explicitly marked as `@Deprecated: Use .spec.expose.router instead`. In kserve mode, Knative Serving handles routing through its own router mechanism. Setting an ingress would conflict with Knative.

**Alternatives considered**:
- Keeping ingress code behind a feature flag: Unnecessary complexity — standalone mode is being replaced, not preserved alongside kserve.

## Decision 5: inferencePlatform configuration

**Decision**: Set `inferencePlatform` to `NIMServiceSpec.InferencePlatform.KSERVE` in the NIM service config template (`application.yml`). The enum is already available in the generated Fabric8 model.

**Rationale**: The template is the base configuration that gets cloned. Setting it in the template means all NIMService manifests will use kserve by default.

**Alternatives considered**:
- Setting it programmatically in the generator: Possible but less clean — the template already defines the base structure.

## Decision 6: Scaling source — Deployment.scaling field

**Decision**: Use the existing `Deployment.scaling` field (inherited by `NimDeployment`) as the source for scaling configuration. Pass `deployment.getScaling()` from `NimDeploymentManager.prepareServiceSpec()` to the manifest generator.

**Rationale**: The base `Deployment` model already has a `Scaling` field with `minReplicas`, `maxReplicas`, `scaleToZeroDelaySeconds`, and `strategy`. The `DeploymentDto` also has `ScalingDto`. NIM deployments inherit these fields. Previously NIM didn't use them, but for kserve mode, scaling is essential.

**Alternatives considered**:
- Adding separate scale fields to NimDeployment: Would duplicate the existing Scaling model and diverge from the established pattern.

## Key Codebase Findings

### Files to modify:
1. `KnativeAnnotations.java` — add `AUTOSCALING_CLASS`, `AUTOSCALING_METRIC` constants
2. `NimManifestGenerator.java` — add scaling, remove ingress, add NIM_CACHE_PATH, add router
3. `NimDeploymentManager.java` — pass scaling, remove useExternalUrl/clusterHost logic
4. `application.yml` — update nim-service-config template (inferencePlatform, expose.router), add autoscaling config properties
5. `NimDeployProperties.java` — add autoscaling config fields (class, metric, target)
6. `NimManifestGeneratorTest.java` — update for new signature, add scaling tests, remove ingress tests
7. `NimDeploymentManagerTest.java` — update for new prepareServiceSpec flow
8. Test resource JSON files — update `nim_service_template.json` and expected output files
9. `docs/configuration.md` — document new NIM autoscaling env vars

### Existing patterns to follow:
- `InferenceManifestGenerator.applyScaling()` — initial-scale computation formula
- `KnativeManifestGenerator.applyScaling()` — annotation-based scaling (min-scale, max-scale, target)
- `NimManifestGenerator.setServedModelNameIfNotSet()` — auto-injection pattern for NIM_CACHE_PATH
