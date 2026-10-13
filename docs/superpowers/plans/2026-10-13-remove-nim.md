# Remove NIM Deployment Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove every NIM-specific API contract, domain type, persistence table, Kubernetes integration, configuration property, test and live doc, and add a migration that deletes existing NIM data.

**Architecture:** Pure removal in five tasks ordered so the build stays green after each: (1) a small exception-handler fix that makes stale `type=NIM` query params return 400; (2) remove the NIM *backend* (Kubernetes client/informer/manifest/manager/config) while the NIM type still exists in the API; (3) remove the NIM *type* from API, domain and persistence; (4) add Flyway `V1.60__RemoveNimDeployment` for H2/Postgres/SQL Server with a cross-vendor migration test; (5) docs, specs, changelog, upgrade plan, final audit. Code removal precedes the migration because `spring.jpa.hibernate.ddl-auto: validate` fails if an entity is mapped to a dropped table, but tolerates extra tables.

**Tech Stack:** Java 21, Spring Boot, Jackson 3 (`tools.jackson`), MapStruct, Hibernate + Envers, Flyway (SQL + Java migrations), fabric8 Kubernetes client + `crd2java`, JUnit 5, MockMvc, Testcontainers (Postgres 17.4, SQL Server 2022).

**Spec:** `docs/superpowers/specs/2026-10-13-remove-nim-design.md`

## Global Constraints

- Branch: `remove-nim-deployments` (already created; spec committed as `de6e1a45`).
- Never edit an applied Flyway migration (V1.1–V1.59 SQL or Java). Old Java migrations that read `nim_deployment` (`V1_34`, `V1_37`, `V1_50`) MUST stay untouched — they still run on fresh databases.
- New migration version: `1.60`, file name `V1.60__RemoveNimDeployment.sql`, added to **all three** vendor dirs under `src/main/resources/db/migration/{H2,POSTGRES,MS_SQL_SERVER}/`.
- Keep: `Nimbus*` JWT classes, `com.nimbusds:nimbus-jose-jwt`, `nvidia.com/gpu` handling (`AppProperties.GPU_LIMIT_KEY`), DCGM GPU metrics, `PoolPrimitivesConverter` (KServe still uses it), historical numbered specs `specs/0NN-*`, old `docs/upgrade-plans/0.15.0–0.21.0.md`, past `docs/INFRA-CHANGELOG.md` entries, speckit-vendored files (`.specify/templates/*.md`, `.specify/extensions.yml`, `.specify/scripts/bash/*.sh`, `.claude/skills/speckit-*/SKILL.md`), `ciliumnetworkpolicy.crd.yaml`.
- Old export ZIPs with a top-level `"nimDeployments"` block: silently ignored (global `FAIL_ON_UNKNOWN_PROPERTIES` is disabled). No shim code.
- `docs/db-schema.md` is auto-generated — never hand-edit. The PostToolUse hook `.claude/hooks/generate-db-schema.sh` regenerates it when migration files change; otherwise run `./gradlew generateDbSchema`.
- Code style: Google Java Style via checkstyle, 180-char lines; `-Werror` on `javac`.
- Gradle: JDK 21 is provisioned in `~/.gradle/jdks`. If Gradle still reports "Cannot find a Java installation matching languageVersion=21", rerun with an init script applying `org.gradle.toolchains:foojay-resolver:1.0.0` (`-I <scratchpad>/foojay.init.gradle`); do not edit `settings.gradle`. Never pipe `./gradlew` through `tail`/`head` — redirect to a file and read it afterwards, so the exit code is not masked.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Existing installation with NIM rows** (deployments, topics, audit revisions, activity feed entries, pending `component_removal`, `NIM_SERVICE` disposable resources) upgrades and starts without FK violations, and the scheduled cleaner does not choke on a now-unknown `NIM_SERVICE` kind → Task 4 migration test seeds every one of these on all three vendors and asserts non-NIM rows survive.
2. **Stale frontend filters the list by `?type=NIM`** → expect 400, not 500 → Task 1 adds the handler (tested with `type=NOT_A_TYPE`); Task 3 adds `type=NIM` → 400.
3. **Stale frontend POSTs `$type: "nim"`** → expect 400 with Jackson "Could not resolve type id 'nim'" message → Task 3 controller test.
4. **Old export ZIP containing `nimDeployments`** → import and import-preview succeed, other components import, no NIM row persisted → Task 3 functional test (runs on H2/Postgres/SQL Server suites).
5. **Operator still has `K8S_NIM_ENABLED=true` / `app.nim.enabled=true` set** → app context must still start and register no NIM beans → Task 2 adds `shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet`; Task 3 deliberately keeps `app.nim.enabled=true` in `H2FunctionalTests` as a stale-config fixture so the test keeps exercising it.

---

### Task 1: Return 400 for unconvertible request parameters

Today `DefaultExceptionHandler` has a catch-all `@ExceptionHandler(Exception.class)` → 500, and no handler for `MethodArgumentTypeMismatchException`. After NIM is removed, `GET /api/v1/deployments?type=NIM` would become a 500.

**Files:**
- Modify: `src/main/java/com/epam/aidial/deployment/manager/web/handler/DefaultExceptionHandler.java` (add handler next to the `MissingRequestValueException` one, ~line 92)
- Test: `src/test/java/com/epam/aidial/deployment/manager/web/controller/none/DeploymentControllerTest.java`

**Interfaces:**
- Produces: `GET` endpoints with enum/UUID query params return 400 `ErrorView` on bad values. Task 3 relies on this for `type=NIM`.

- [ ] **Step 1: Write the failing test** — add to `DeploymentControllerTest`:

```java
    @Test
    void testGetAllDeployments_withUnknownType_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/deployments").param("type", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("NOT_A_TYPE")));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testFast --tests "com.epam.aidial.deployment.manager.web.controller.none.DeploymentControllerTest" > build/t1.log 2>&1; echo exit=$?`
Expected: FAIL — status 500 instead of 400.

- [ ] **Step 3: Add the handler** — in `DefaultExceptionHandler`, after `handleMissingRequestValueError`, and add `import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;` in sorted position:

```java
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorView handleArgumentTypeMismatchError(HttpServletRequest req, MethodArgumentTypeMismatchException ex) {
        logUncaught(ex);
        return new ErrorView(req, HttpStatus.BAD_REQUEST,
                "Invalid value '%s' for parameter '%s'".formatted(ex.getValue(), ex.getName()));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testFast --tests "com.epam.aidial.deployment.manager.web.controller.none.DeploymentControllerTest" > build/t1.log 2>&1; echo exit=$?`
Expected: exit=0.

- [ ] **Step 5: Update the api-conventions spec** — in `specs/api-conventions/spec.md`, in the requirement that lists which exceptions map to 400 (search for `HttpMessageNotReadableException`), add `MethodArgumentTypeMismatchException` (unconvertible path/query parameter, e.g. unknown enum value) → 400 with message `Invalid value '<value>' for parameter '<name>'`. If no such list exists, add a scenario under the ErrorView requirement:

```markdown
#### Scenario: Unconvertible request parameter
- **WHEN** a path or query parameter cannot be converted to its declared type (e.g. `GET /api/v1/deployments?type=NOT_A_TYPE`)
- **THEN** the system responds with 400 and an `ErrorView` whose `message` is `Invalid value '<value>' for parameter '<name>'`
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/epam/aidial/deployment/manager/web/handler/DefaultExceptionHandler.java \
        src/test/java/com/epam/aidial/deployment/manager/web/controller/none/DeploymentControllerTest.java \
        specs/api-conventions/spec.md
git commit -m "fix: return 400 for unconvertible request parameters

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Remove the NIM backend (Kubernetes, manifests, manager, config)

After this task the NIM type still exists in the API/domain/DB, but nothing can deploy it — exactly the behaviour today with the default `K8S_NIM_ENABLED=false` (`DeploymentManagerProvider` finds no manager bean). This keeps the build green while the large type removal happens in Task 3.

**Files:**
- Delete (main):
  - `src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java`
  - `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java`
  - `src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimProbeConverter.java`
  - `src/main/java/com/epam/aidial/deployment/manager/utils/mapping/NimMappers.java`
  - `src/main/java/com/epam/aidial/deployment/manager/kubernetes/nim/NimClient.java`
  - `src/main/java/com/epam/aidial/deployment/manager/kubernetes/nim/K8sNimClient.java`
  - `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/NimServiceEventHandler.java`
  - `src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/registration/NimServiceInformerRegistration.java`
  - `src/main/java/com/epam/aidial/deployment/manager/configuration/NimDeployProperties.java`
  - `src/main/resources/kubernetes/crd/nimservice.crd.yaml` (removes the generated `com.nvidia.apps.v1alpha1.*` classes)
- Delete (test):
  - `src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java`
  - `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java`
  - `src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimProbeConverterTest.java`
  - `src/test/java/com/epam/aidial/deployment/manager/kubernetes/nim/K8sNimClientTest.java`
  - `src/test/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/NimServiceEventHandlerTest.java`
  - `src/test/resources/manifest/nim_service_kserve_mode.json`, `nim_service_legacy_external_url.json`, `nim_service_template.json`, `nim_service_with_envs.json`, `nim_service_with_resources.json`
- Modify:
  - `src/main/java/com/epam/aidial/deployment/manager/configuration/AppProperties.java`
  - `src/main/java/com/epam/aidial/deployment/manager/cleanup/resource/DisposableResourceCleaner.java`
  - `src/main/java/com/epam/aidial/deployment/manager/cleanup/resource/DisposableResourceManager.java`
  - `src/main/java/com/epam/aidial/deployment/manager/cleanup/resource/model/K8sResourceKind.java`
  - `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractDeploymentManager.java` (Javadoc only)
  - `src/main/java/com/epam/aidial/deployment/manager/service/deployment/AbstractModelDeploymentManager.java` (Javadoc only)
  - `src/main/java/com/epam/aidial/deployment/manager/service/manifest/PoolPrimitivesConverter.java` (Javadoc only)
  - `src/main/java/com/epam/aidial/deployment/manager/service/deployment/metrics/ServingMetricsCollector.java` (Javadoc only)
  - `src/main/resources/application.yml`
  - `local_env/docker-compose.yml`
  - `src/test/java/com/epam/aidial/deployment/manager/cleanup/DisposableResourceCleanerTest.java`
  - `src/test/java/com/epam/aidial/deployment/manager/functional/config/K8sLocalConfiguration.java`
  - `src/test/java/com/epam/aidial/deployment/manager/service/manifest/PoolPrimitivesConverterTest.java`
  - `src/test/resources/archunit_store/bd84223a-e646-43f4-a2bb-1ca1c7505013`
  - plus any further compile errors surfaced in Step 4 (functional tests that inject NIM beans, e.g. `DeploymentFunctionalTest`, `DeploymentMetricsFunctionalTest`)

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `K8sResourceKind` = `{SECRET, CONFIGMAP, JOB, KNATIVE_SERVICE, INFERENCE_SERVICE, CILIUM_NETWORK_POLICY}`; `DisposableResourceManager` without `markNimServiceResourceForCleanup` / `saveNimServiceResource`; `AppProperties` without `nimServiceConfig`, `nimServiceExposeIngressConfig`, `cloneNimServiceConfig()`, `cloneNimServiceExposeIngressConfig()`. Task 4's migration deletes `NIM_SERVICE` rows that this enum change would otherwise make undeserializable.

- [ ] **Step 1: Write the stale-flag test** — add to `src/test/java/com/epam/aidial/deployment/manager/functional/tests/DeploymentFunctionalTest.java` (the H2/Postgres/SQL Server suites all set `app.nim.enabled=true`, so this pins Review Focus #5 for this task):

```java
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet() {
        assertThat(applicationContext.getEnvironment().getProperty("app.nim.enabled")).isEqualTo("true");
        assertThat(applicationContext.getBeanDefinitionNames())
                .filteredOn(name -> !name.contains("imbus"))
                .noneMatch(name -> name.startsWith("nim") || name.contains("Nim") || name.contains("NIM"));
    }
```

Add `import org.springframework.context.ApplicationContext;` (skip the field if the class already autowires one). The match is deliberately **case-sensitive camelCase**: a case-insensitive `contains("nim")` false-positives on `applicationImageDefinition…` ("applicatio**nIm**age"). `imbus` excludes the unrelated `Nimbus*` JWT beans.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew testFast --tests "com.epam.aidial.deployment.manager.functional.H2FunctionalTests" > build/t2.log 2>&1; echo exit=$?`
Expected: FAIL in `shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet` — beans like `nimDeploymentManager`, `k8sNimClient`, `nimServiceEventHandler` are listed.

- [ ] **Step 3: Delete the backend files** listed under *Delete (main)* and *Delete (test)*:

```bash
git rm src/main/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManager.java \
       src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGenerator.java \
       src/main/java/com/epam/aidial/deployment/manager/service/manifest/NimProbeConverter.java \
       src/main/java/com/epam/aidial/deployment/manager/utils/mapping/NimMappers.java \
       -r src/main/java/com/epam/aidial/deployment/manager/kubernetes/nim \
       src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/NimServiceEventHandler.java \
       src/main/java/com/epam/aidial/deployment/manager/kubernetes/informer/registration/NimServiceInformerRegistration.java \
       src/main/java/com/epam/aidial/deployment/manager/configuration/NimDeployProperties.java \
       src/main/resources/kubernetes/crd/nimservice.crd.yaml \
       src/test/java/com/epam/aidial/deployment/manager/service/deployment/NimDeploymentManagerTest.java \
       src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimManifestGeneratorTest.java \
       src/test/java/com/epam/aidial/deployment/manager/service/manifest/NimProbeConverterTest.java \
       -r src/test/java/com/epam/aidial/deployment/manager/kubernetes/nim \
       src/test/java/com/epam/aidial/deployment/manager/kubernetes/informer/handler/NimServiceEventHandlerTest.java \
       src/test/resources/manifest/nim_service_kserve_mode.json \
       src/test/resources/manifest/nim_service_legacy_external_url.json \
       src/test/resources/manifest/nim_service_template.json \
       src/test/resources/manifest/nim_service_with_envs.json \
       src/test/resources/manifest/nim_service_with_resources.json
```

(`git rm -r` takes `-r` once; if your shell rejects the interleaved flag, run the two directory removals as separate `git rm -r <dir>` commands.)

- [ ] **Step 4: Edit the remaining main code**

`AppProperties.java` — delete these imports and members (keep `Serialization`, still used by `cloneInferenceServiceConfig`):

```java
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Ingress;
...
    private NIMService nimServiceConfig;
    private Ingress nimServiceExposeIngressConfig;
...
    public NIMService cloneNimServiceConfig() {
        return Serialization.clone(nimServiceConfig);
    }

    public Ingress cloneNimServiceExposeIngressConfig() {
        return Serialization.clone(nimServiceExposeIngressConfig);
    }
```

`K8sResourceKind.java` — becomes:

```java
public enum K8sResourceKind {
    SECRET,
    CONFIGMAP,
    JOB,
    KNATIVE_SERVICE,
    INFERENCE_SERVICE,
    CILIUM_NETWORK_POLICY,
}
```

`DisposableResourceCleaner.java` — delete `import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;`, the field `private final K8sNimClient k8sNimClient;`, the line `resourcesToDelete.addAll(clean(resourcesByKind.get(K8sResourceKind.NIM_SERVICE.name())));`, and the switch arm:

```java
            case NIM_SERVICE -> {
                k8sNimClient.deleteService(namespace, name);
                log.debug(CLEANED_UP_MESSAGE_FORMAT, "NIM Service", name, namespace);
            }
```

`DisposableResourceManager.java` — delete both methods:

```java
    public List<DisposableResource> markNimServiceResourceForCleanup(String id, String serviceName, String namespace) {
        return markServiceResourceForCleanup(id, serviceName, namespace, K8sResourceKind.NIM_SERVICE);
    }
...
    public void saveNimServiceResource(String id, String serviceName, String namespace) {
        saveServiceResource(id, serviceName, namespace, K8sResourceKind.NIM_SERVICE);
    }
```

(Include any Javadoc directly above each.)

Javadoc rewording (no behaviour change):
- `AbstractDeploymentManager.java:769` — `No-op by default (e.g. NIM, which keeps its own NGC pull secret); overridden by managers that` → `No-op by default; overridden by managers that`.
- `AbstractDeploymentManager.java:781` — `successful redeploy. No-op by default (e.g. NIM); overridden together with` → `successful redeploy. No-op by default; overridden together with`.
- `AbstractModelDeploymentManager.java:16` — `Base class for model serving deployments (e.g., KServe and NIM).` → `Base class for model serving deployments (KServe).`
- `PoolPrimitivesConverter.java:14-19` Javadoc → 
```java
/**
 * Bridges the canonical Fabric8 pool primitives (Affinity, Toleration) used in NodePoolProperties
 * into CRD-specific types generated for KServe (io.kserve.serving.v1beta1.inferenceservicespec.predictor.Affinity etc.).
 * The target types are JSON-equivalent to the Fabric8 model, so a Jackson convertValue round-trip is lossless.
 */
```
- `ServingMetricsCollector.java:9` — replace `(e.g. NIM)` with `(e.g. a new model-serving runtime)`.

`application.yml` — delete the whole `app.nim:` block (lines `  nim:` through `      kserve-mode-enabled: ${K8S_NIM_DEPLOYMENT_KSERVE_MODE_ENABLED:false}`), the whole `app.nim-service-config:` block (through `          port: 8000`), and the whole `app.nim-service-expose-ingress-config:` block (through `      ingressClassName: ${K8S_NIM_INGRESS_CLASS_NAME:nginx}`). Do **not** touch `app.validation.resources.max-storage-size` yet (Task 3).

`local_env/docker-compose.yml` — delete the line `      K8S_NIM_ENABLED: ${K8S_NIM_ENABLED:-false}`.

- [ ] **Step 5: Edit tests**

- `DisposableResourceCleanerTest.java`: remove the `K8sNimClient` import and `@Mock private K8sNimClient k8sNimClient;`, the two `buildDisposableResource(buildK8sResourceReference(K8sResourceKind.NIM_SERVICE, "nim-1"/"nim-2"))` entries, and the `verify(k8sNimClient)...` lines. Keep the remaining ordering assertions intact (KNATIVE_SERVICE → INFERENCE_SERVICE → JOB → …).
- `K8sLocalConfiguration.java`: remove the `K8sNimClient`/`NimClient` bean(s) and imports.
- `PoolPrimitivesConverterTest.java`: remove the test cases converting into `com.nvidia.apps.v1alpha1.*` types; keep the KServe cases.
- `src/test/resources/archunit_store/bd84223a-e646-43f4-a2bb-1ca1c7505013`: delete the single line beginning `Method <com.epam.aidial.deployment.manager.kubernetes.informer.handler.NimServiceEventHandler.reconcile(`. The store is frozen with `allowStoreUpdate=false`, so remove only that line by hand; do not regenerate the file.

- [ ] **Step 6: Compile and fix fallout**

Run: `./gradlew compileJava compileTestJava > build/t2-compile.log 2>&1; echo exit=$?`
Expected: exit=0. If not, every remaining error is a reference to a deleted class; remove that usage (a NIM-only test method → delete the method; a shared helper → drop the NIM branch). Repeat until green. Then check for leftovers:

Run: `git grep -n -E "NimClient|K8sNimClient|NimManifestGenerator|NimProbeConverter|NimMappers|NimDeployProperties|NimServiceEventHandler|NimServiceInformerRegistration|NIM_SERVICE|com\.nvidia\.apps|nimServiceConfig|nimServiceExposeIngressConfig" -- src local_env`
Expected: no output.

- [ ] **Step 7: Run tests**

Run: `./gradlew testFast > build/t2.log 2>&1; echo exit=$?`
Expected: exit=0, including `shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet`. If a pre-existing functional test creates a NIM deployment and then deploys it, delete that test method (deploying NIM is no longer possible); keep tests that only CRUD the record (Task 3 removes those).

- [ ] **Step 8: Checkstyle**

Run: `./gradlew checkstyleMain checkstyleTest > build/t2-cs.log 2>&1; echo exit=$?`
Expected: exit=0 (fix unused imports if flagged).

- [ ] **Step 9: Update capability specs touched by this task**
  - `specs/kubernetes-cleanup/spec.md`: remove `NIM_SERVICE` from the `K8sResourceKind` lists (lines ~17 and ~105); change the deletion order (line ~98) to `KNATIVE_SERVICE → INFERENCE_SERVICE → JOB → CONFIGMAP → SECRET → CILIUM_NETWORK_POLICY → IMAGE`; change line ~91 to "KNative Services and Inference Services are deleted before Jobs, ConfigMaps, Secrets, and CiliumNetworkPolicies".
  - `specs/kubernetes-manifests/spec.md`: delete the whole `### Requirement: NIM-specific resources generated for NIM deployments` section (through its three scenarios); in Key Terms drop the `NIM resources` and `NimProbeConverter` bullets and `NIM` from the Manifest-strategy and Feature-flag bullets (`app.nim.enabled`); in the auto-pull-secrets requirement replace `the predictor and NIM workloads are out of scope (NIM keeps its existing `spec.image.pullSecrets`)` with `the predictor workload is out of scope`; in the progress-deadline requirement change "KServe and NIM only" → "KServe only", rename scenario `Fallback deadline for KServe and NIM without startup probe` → `Fallback deadline for KServe without startup probe` and its WHEN to "a KServe deployment"; delete line ~169 `- **NIM Service**: …`; in Strategy routing drop `, NIM for NIM`; in Implementation Notes delete the NIM manifest generator / NIM probe converter / NIM client bullets, drop `NimMappers` and `app.nim.enabled`, and drop `nim-deployments` from Related specs.

- [ ] **Step 10: Commit**

```bash
git add -A src local_env specs/kubernetes-cleanup/spec.md specs/kubernetes-manifests/spec.md
git commit -m "refactor: remove NIM Kubernetes integration, manifests and manager

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Remove the NIM type from API, domain, persistence and export/import

**Files:**
- Delete (main):
  - `src/main/java/com/epam/aidial/deployment/manager/model/deployment/NimDeployment.java`
  - `src/main/java/com/epam/aidial/deployment/manager/model/deployment/CreateNimDeployment.java`
  - `src/main/java/com/epam/aidial/deployment/manager/model/deployment/NgcRegistrySource.java`
  - `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/NimDeploymentEntity.java`
  - `src/main/java/com/epam/aidial/deployment/manager/dao/entity/deployment/PersistenceNgcRegistrySource.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/CreateNimDeploymentRequestDto.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/NimDeploymentDto.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/NimDeploymentSourceDto.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/dto/deployment/NimDeploymentNgcRegistrySourceDto.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/dto/internal/NimDeploymentInternalDto.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/validation/ValidStorageSize.java`
  - `src/main/java/com/epam/aidial/deployment/manager/web/validation/StorageSizeValidator.java`
  - `src/main/java/com/epam/aidial/deployment/manager/utils/KubernetesQuantityParser.java`
- Delete (test):
  - `src/test/java/com/epam/aidial/deployment/manager/web/validation/StorageSizeValidatorTest.java`
  - `src/test/java/com/epam/aidial/deployment/manager/utils/KubernetesQuantityParserTest.java`
- Modify (main): `DeploymentTypeDto`, `CreateDeploymentRequestDto`, `DeploymentDto`, `DeploymentInternalDto`, `ExportConfigComponentTypeDto`, `ImportConfigPreviewDto`, `Deployment`, `Source`, `PersistenceSource`, `ActivityResourceType`, `ExportConfigComponentType`, `ExportConfig`, `ImportConfigPreview`, `DeploymentDtoMapper`, `DeploymentMapper`, `PersistenceDeploymentMapper`, `ExportConfigMapper`, `ImportConfigDtoMapper`, `ImportConfigValidator`, `DeploymentRepository`, `AuditActivityMapper`, `DeploymentManagerProvider`, `DeploymentSourceValidator`, `WorkloadClassifier`, `ConfigExporter`, `ConfigImporter`, `DeploymentImporter`, `DeploymentImportPreviewer`, `application.yml` (all under `src/main/java/com/epam/aidial/deployment/manager/...` — exact lines below).
- Modify (test): `DeploymentControllerTest`, `ConfigExportImportFunctionalTest`, `DeploymentRepositoryTest`, `DeploymentExportMixInTest`, `AuditActivityMapperTest`, `ConfigExporterTest`, `DeploymentImporterTest`, `ImportConfigValidatorTest`, `WorkloadClassifierTest`, `NodePoolServiceTest`, `DeploymentMetricsFunctionalTest`, `FunctionalTestHelper`, `DeploymentFunctionalTest`, `H2FunctionalTests`, `PostgresFunctionalTests`, `SqlServerFunctionalTests`, `src/test/resources/archunit_store/29386af3-8f95-4deb-b905-fdb36d5aa2f0`. `src/test/resources/config/expected_export_config_for_import.json` is **kept unchanged** — its `nimDeployments` block becomes the legacy-ZIP fixture.

**Interfaces:**
- Consumes: Task 1's 400 handler (for `type=NIM`); Task 2's removal of every NIM backend class.
- Produces: `DeploymentTypeDto` = `{MCP, ADAPTER, APPLICATION, INTERCEPTOR, INFERENCE}` (order otherwise unchanged); `Source` subtypes `internal_image`, `image_reference`, `huggingface`; `ExportConfigComponentType`/`ExportConfigComponentTypeDto` without `NIM_DEPLOYMENT`; `ExportConfig`/`ImportConfigPreview`/`ImportConfigPreviewDto` without `nimDeployments`; `ActivityResourceType` without `NimDeployment`. No Java type maps to table `nim_deployment` or `nim_deployment_aud` (Task 4 drops them).

- [ ] **Step 1: Write the failing regression tests**

In `DeploymentControllerTest` (uses `post`, `get`, `status`, `jsonPath`, `containsString`, `MediaType` already imported):

```java
    @Test
    void testCreateDeployment_withLegacyNimType_returnsBadRequest() throws Exception {
        var nimRequestJson = """
                {
                  "$type": "nim",
                  "name": "legacy-nim",
                  "displayName": "Legacy NIM",
                  "source": {"$type": "ngc_registry", "imageRef": "nvcr.io/nim/meta/llama3:1.0.0"},
                  "resources": {"limits": {}, "requests": {}}
                }
                """;

        mockMvc.perform(post("/api/v1/deployments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nimRequestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Could not resolve type id 'nim'")));
    }

    @Test
    void testGetAllDeployments_withLegacyNimTypeFilter_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/deployments").param("type", "NIM"))
                .andExpect(status().isBadRequest());
    }
```

In `ConfigExportImportFunctionalTest` (has `configTransferService`, `deploymentService`, `buildImportZipFromResource()` backed by `/config/expected_export_config_for_import.json`, which contains `nimDeployments."nim-dep-exp"` and `mcpDeployments."mcp-dep-exp"`):

```java
    @Test
    void import_ignoresLegacyNimDeploymentsBlock() throws Exception {
        MultipartFile zipFile = buildImportZipFromResource();

        ImportConfigPreview preview = configTransferService.getImportConfigPreview(zipFile, ConflictResolutionPolicy.OVERWRITE);
        assertThat(preview.getMcpDeployments()).isNotEmpty();

        configTransferService.importConfig(zipFile, ConflictResolutionPolicy.OVERWRITE);

        assertThat(deploymentService.getDeployment("mcp-dep-exp")).isPresent();
        assertThat(deploymentService.getDeployment("nim-dep-exp")).isEmpty();
    }
```

If, after Step 8, Jackson 3's wording differs from `Could not resolve type id 'nim'`, read the actual `$.message` from the failure and assert on its stable fragment that names `nim` — do not weaken the test to status-only.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew testFast --tests "com.epam.aidial.deployment.manager.web.controller.none.DeploymentControllerTest" --tests "com.epam.aidial.deployment.manager.functional.H2FunctionalTests" > build/t3.log 2>&1; echo exit=$?`
Expected: FAIL — the NIM POST is accepted or fails with a different message; `type=NIM` returns 200; `nim-dep-exp` is imported and therefore present.

- [ ] **Step 3: Delete the NIM type files** listed under *Delete (main)* and *Delete (test)* with `git rm`.

- [ ] **Step 4: Edit the web layer**

- `web/dto/DeploymentTypeDto.java`: delete `    NIM,`.
- `web/dto/deployment/CreateDeploymentRequestDto.java`: delete `        @JsonSubTypes.Type(value = CreateNimDeploymentRequestDto.class, name = "nim"),`.
- `web/dto/deployment/DeploymentDto.java`: delete `        @JsonSubTypes.Type(value = NimDeploymentDto.class, name = "nim"),`.
- `web/dto/internal/DeploymentInternalDto.java`: delete `        @JsonSubTypes.Type(value = NimDeploymentInternalDto.class, name = "nim"),`.
- `web/dto/config/ExportConfigComponentTypeDto.java`: delete `    NIM_DEPLOYMENT,`.
- `web/dto/config/ImportConfigPreviewDto.java`: delete the `NimDeploymentDto` import and the record component `        List<ImportComponentDto<NimDeploymentDto>> nimDeployments,`.
- `web/mapper/ImportConfigDtoMapper.java`: delete the `NimDeploymentDto` import and the constructor argument
  ```java
                mapList(preview.getNimDeployments(),
                        dep -> (NimDeploymentDto) deploymentDtoMapper.toDeploymentDto(dep)),
  ```
  (verify the remaining positional arguments line up with `ImportConfigPreviewDto`'s components).
- `web/mapper/ExportConfigMapper.java`: delete `import static ...ExportConfigComponentTypeDto.NIM_DEPLOYMENT;` and the stream element
  ```java
                config.getNimDeployments().values().stream()
                        .map(d -> toComponentInfoDto(d, NIM_DEPLOYMENT)),
  ```
- `web/validation/ImportConfigValidator.java`: delete `        validateDeployments(config.getNimDeployments(), ExportConfigComponentType.NIM_DEPLOYMENT, errors);`.
- `web/mapper/DeploymentDtoMapper.java`: delete imports `CreateNimDeployment`, `NgcRegistrySource`, `NimDeployment`, `CreateNimDeploymentRequestDto`, `NimDeploymentDto`, `NimDeploymentNgcRegistrySourceDto`, `NimDeploymentInternalDto`; delete the four `@SubclassMapping` lines at ~120, ~132, ~145, ~154 that mention NIM types; delete the methods `setCreateNimSource`, `setNimRequestDtoSource`, `setNimDtoSource` (each with its `@AfterMapping`); in `toDeploymentTypeDto` delete
  ```java
        } else if (deployment instanceof NimDeployment) {
            return DeploymentTypeDto.NIM;
  ```

- [ ] **Step 5: Edit domain, service and persistence**

- `model/deployment/Deployment.java`: delete `        @JsonSubTypes.Type(value = NimDeployment.class, name = "nim"),`.
- `model/deployment/Source.java`: delete `        @JsonSubTypes.Type(value = NgcRegistrySource.class, name = "ngc_registry"),` and `NgcRegistrySource` from any `permits` clause.
- `dao/entity/deployment/PersistenceSource.java`: delete `        @JsonSubTypes.Type(value = PersistenceNgcRegistrySource.class, name = "ngc_registry"),` and from `permits` if present.
- `model/audit/ActivityResourceType.java`: delete `    NimDeployment,`.
- `model/config/ExportConfigComponentType.java`: delete `    NIM_DEPLOYMENT,`.
- `model/config/ExportConfig.java`: delete the `NimDeployment` import and the field `private Map<String, NimDeployment> nimDeployments = new LinkedHashMap<>();` together with its annotations (`@Builder.Default` etc.) on the line(s) above.
- `model/config/ImportConfigPreview.java`: delete the `NimDeployment` import and `private List<ImportComponent<NimDeployment>> nimDeployments = new ArrayList<>();` with its annotations.
- `mapper/DeploymentMapper.java`: delete imports `CreateNimDeployment`, `NimDeployment` and the two `@SubclassMapping` lines (~43, ~72).
- `dao/mapper/PersistenceDeploymentMapper.java`: delete imports `NimDeploymentEntity`, `PersistenceNgcRegistrySource`, `NgcRegistrySource`, `NimDeployment`; delete the four `@SubclassMapping` lines (~59, ~65, ~76, ~82); delete the block
  ```java
        if (existingEntity instanceof NimDeploymentEntity existingNim
                && updatedEntity instanceof NimDeploymentEntity updatedNim) {
            existingNim.setContainerGrpcPort(updatedNim.getContainerGrpcPort());
            existingNim.setStorageSize(updatedNim.getStorageSize());
        }
  ```
  (check the surrounding `if/else if` chain stays syntactically valid).
- `dao/repository/DeploymentRepository.java`: delete the import and `            case NIM -> NimDeploymentEntity.class;`.
- `dao/audit/mapper/AuditActivityMapper.java`: delete the import and `            Map.entry(NimDeploymentEntity.class, ActivityResourceType.NimDeployment),`.
- `service/deployment/DeploymentManagerProvider.java`: delete imports `CreateNimDeployment`, `NimDeployment` and `            case CreateNimDeployment ignored -> NimDeployment.class;`.
- `service/deployment/DeploymentSourceValidator.java`: delete imports `CreateNimDeployment`, `NgcRegistrySource` and `            case CreateNimDeployment ignored -> source instanceof NgcRegistrySource;`.
- `service/nodepool/WorkloadClassifier.java`: delete the import and `            case CreateNimDeployment ignored -> true;`.
- `service/config/ConfigExporter.java`: delete the import; change
  ```java
                case MCP_DEPLOYMENT, ADAPTER_DEPLOYMENT, INTERCEPTOR_DEPLOYMENT, APPLICATION_DEPLOYMENT,
                        NIM_DEPLOYMENT, INFERENCE_DEPLOYMENT ->
  ```
  to
  ```java
                case MCP_DEPLOYMENT, ADAPTER_DEPLOYMENT, INTERCEPTOR_DEPLOYMENT, APPLICATION_DEPLOYMENT,
                        INFERENCE_DEPLOYMENT ->
  ```
  and delete `            case NimDeployment ignored -> config.getNimDeployments().put(key, (NimDeployment) sanitized);`.
- `service/config/ConfigImporter.java`: delete `                + config.getNimDeployments().size()`.
- `service/config/imports/DeploymentImporter.java`: delete `        importMap(config.getNimDeployments(), policy);`.
- `service/config/previews/DeploymentImportPreviewer.java`: delete `        preview.getNimDeployments().addAll(previewMap(config.getNimDeployments(), policy));`.
- `src/main/resources/application.yml`: delete `      max-storage-size: ${RESOURCES_STORAGE_MAX_SIZE:200Gi}` under `app.validation.resources`.

- [ ] **Step 6: Edit tests**

- `PostgresFunctionalTests`, `SqlServerFunctionalTests`, `K8sFunctionalTests`: delete the `"app.nim.enabled=..."` property line.
- `H2FunctionalTests`: **keep** `"app.nim.enabled=true"` as a deliberate stale-config fixture (Review Focus #5) and put a comment on the line above it: `// Legacy flag deliberately kept: NIM was removed in 0.22.0 and the property must be ignored`.
- `DeploymentFunctionalTest`: keep `shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet`, but since only the H2 suite still sets the flag, relax its first assertion to run only when the flag is present:
  ```java
    @Test
    public void shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet() {
        assertThat(applicationContext.getBeanDefinitionNames())
                .filteredOn(name -> !name.contains("imbus"))
                .noneMatch(name -> name.startsWith("nim") || name.contains("Nim") || name.contains("NIM"));
    }
  ```
  Also remove any remaining NIM create/update test methods and NIM-specific helper calls.
- `FunctionalTestHelper`: delete NIM request/entity builder methods (e.g. `createNimDeploymentRequest`) and their imports.
- `ConfigExportImportFunctionalTest`: delete NIM export/import test methods and every `getNimDeployments()` assertion (e.g. `assertThat(preview.getNimDeployments()).isEmpty();` and NIM lines inside `assertPreviewAction`). Keep `import_ignoresLegacyNimDeploymentsBlock`. If `assertPreviewAction` asserted "all 8 entity types", update its comment/count to the remaining types.
- `DeploymentRepositoryTest`: delete the import and `                Arguments.of(DeploymentTypeDto.NIM, NimDeploymentEntity.class)` (fix the trailing comma on the previous `Arguments.of(...)`).
- `DeploymentExportMixInTest`: delete the import and `                new NimDeployment(),`.
- `AuditActivityMapperTest`, `ConfigExporterTest`, `DeploymentImporterTest`, `ImportConfigValidatorTest`, `WorkloadClassifierTest`, `NodePoolServiceTest`, `DeploymentMetricsFunctionalTest`: delete NIM-only test methods/parameter rows and NIM imports; where a parameterized source lists all deployment types, drop the NIM row.
- `src/test/resources/archunit_store/29386af3-8f95-4deb-b905-fdb36d5aa2f0`: delete the single line containing `gets field <com.epam.aidial.deployment.manager.web.dto.DeploymentTypeDto.NIM>`.

- [ ] **Step 7: Compile, then grep for leftovers**

Run: `./gradlew compileJava compileTestJava > build/t3-compile.log 2>&1; echo exit=$?`
Expected: exit=0. Fix any remaining reference to a deleted type by removing the NIM branch.

Run: `git grep -n -E "NimDeployment|CreateNimDeployment|NgcRegistrySource|NimDeploymentEntity|ngc_registry|NIM_DEPLOYMENT|DeploymentTypeDto\.NIM|getNimDeployments|nimDeployments|StorageSizeValidator|ValidStorageSize|KubernetesQuantityParser|max-storage-size|app\.nim" -- src ':!src/main/java/db/migration' ':!src/test/resources/config/expected_export_config_for_import.json'`
Expected: only the legacy regression tests (`"$type": "nim"` / `ngc_registry` JSON in `DeploymentControllerTest`, `nim-dep-exp` in `ConfigExportImportFunctionalTest`), `shouldNotRegisterAnyNimBeans_evenWhenLegacyNimFlagIsSet`, and the commented stale `"app.nim.enabled=true"` in `H2FunctionalTests`.

- [ ] **Step 8: Run tests**

Run: `./gradlew testFast > build/t3.log 2>&1; echo exit=$?`
Expected: exit=0, including the three regression tests from Step 1.

- [ ] **Step 9: Checkstyle**

Run: `./gradlew checkstyleMain checkstyleTest > build/t3-cs.log 2>&1; echo exit=$?`
Expected: exit=0.

- [ ] **Step 10: Update capability specs touched by this task**
  - `specs/deployments/spec.md`: line ~11 → "Three variants exist: `InternalImageSource` …, `ImageReferenceSource` …, `HuggingFaceSource` (`$type: "huggingface"` — HuggingFace model)." (drop `NgcRegistrySource`); line ~13 → "**Model-source deployment**: A deployment type (Inference) that references a model source directly. Inference uses `HuggingFaceSource`."; line ~47 "NIM or KServe deployments, which have distinct failure states in their respective operators" → "KServe deployments, which have distinct failure states in their operator"; line ~93 "(for NIM / KServe-Inference workloads, when set)" → "(for KServe-Inference workloads, when set)"; line ~120 example `ngc_registry` for an MCP deployment → `huggingface` for an MCP deployment; line ~128 `type: INFERENCE|NIM` → `type: INFERENCE`; in the class trees (~430, ~439, ~450) delete the `NimDeploymentDto`, `NgcRegistrySource`, `NimDeploymentEntity` lines; line ~453 "NIM and Inference subtype entities" → "Inference subtype entities"; lines ~485/~487 drop `NgcRegistrySource` and `PersistenceNgcRegistrySource`. Add one line under the base requirement: `NIM deployment type removed in 0.22.0 — see `docs/superpowers/specs/2026-10-13-remove-nim-design.md`.`
  - `specs/export-import/spec.md`: drop `NIM_DEPLOYMENT` from the `ExportConfigComponentType` list (line ~10); line ~75 "NIM or Inference deployments" → "Inference deployments"; add a scenario:
    ```markdown
    #### Scenario: Legacy NIM entries in an imported archive are ignored
    - **WHEN** an archive produced by a release before 0.22.0 contains a top-level `nimDeployments` map
    - **THEN** the map is ignored (unknown properties are not rejected), no NIM data is persisted, and all other components are previewed and imported normally
    ```
  - `specs/auditing/spec.md`: remove `NimDeployment` from the audited entity list (line ~15) and `nim-deployments` from Related specs (line ~162).
  - `specs/deployment-metrics/spec.md`: line ~34 `(MCP/adapter/application/interceptor/NIM)` → `(MCP/adapter/application/interceptor)`; drop `nim-deployments` from Related specs (line ~173).
  - `specs/image-definitions/spec.md` line ~15: "there are no NIM or INFERENCE image definition subtypes" → "there is no INFERENCE image definition subtype".

- [ ] **Step 11: Commit**

```bash
git add -A src specs/deployments/spec.md specs/export-import/spec.md specs/auditing/spec.md specs/deployment-metrics/spec.md specs/image-definitions/spec.md
git commit -m "refactor!: remove NIM deployment type from API, domain and persistence

BREAKING CHANGE: deployment type NIM, source type ngc_registry, export
component type NIM_DEPLOYMENT and RESOURCES_STORAGE_MAX_SIZE are removed.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Migration `V1.60__RemoveNimDeployment` (H2, Postgres, SQL Server)

**Files:**
- Create: `src/main/resources/db/migration/H2/V1.60__RemoveNimDeployment.sql`
- Create: `src/main/resources/db/migration/POSTGRES/V1.60__RemoveNimDeployment.sql`
- Create: `src/main/resources/db/migration/MS_SQL_SERVER/V1.60__RemoveNimDeployment.sql`
- Create: `src/test/java/com/epam/aidial/deployment/manager/functional/tests/NimRemovalMigrationFunctionalTest.java`
- Modify: `src/test/java/com/epam/aidial/deployment/manager/functional/H2FunctionalTests.java`, `PostgresFunctionalTests.java`, `SqlServerFunctionalTests.java` (add a nested subclass; SQL Server also gets a `getContainer()` accessor)
- Regenerated (do not hand-edit): `docs/db-schema.md`

**Interfaces:**
- Consumes: Task 3 (no entity maps to `nim_deployment`/`nim_deployment_aud`, so `ddl-auto: validate` passes once the tables are gone); Task 2 (`K8sResourceKind` has no `NIM_SERVICE`).
- Produces: schema at 1.60 without `nim_deployment`, `nim_deployment_aud`; no rows referencing NIM deployments.

- [ ] **Step 1: Write the abstract migration test**

`NimRemovalMigrationFunctionalTest.java`. It migrates a **separate, empty** database to 1.59, seeds NIM + MCP data, migrates to latest, and asserts. It does not touch the Spring test datasource.

```java
package com.epam.aidial.deployment.manager.functional.tests;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class NimRemovalMigrationFunctionalTest {

    private static final String NIM_ID = "nim-legacy-1";
    private static final String MCP_ID = "mcp-keep-1";

    /** A fresh, empty database for the vendor under test. */
    protected abstract DataSource freshDataSource() throws SQLException;

    /** Vendor directory name: H2, POSTGRES or MS_SQL_SERVER. */
    protected abstract String vendor();

    /** SQL expression binding a JSON string parameter for this vendor, e.g. {@code CAST(? AS jsonb)}. */
    protected abstract String jsonParam();

    @Test
    void v1_60_shouldRemoveAllNimDataAndKeepOtherDeployments() throws Exception {
        DataSource dataSource = freshDataSource();

        flyway(dataSource, MigrationVersion.fromVersion("1.59")).migrate();
        try (Connection connection = dataSource.getConnection()) {
            seed(connection);
        }

        flyway(dataSource, MigrationVersion.LATEST).migrate();

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "nim_deployment")).isFalse();
            assertThat(tableExists(connection, "nim_deployment_aud")).isFalse();
            assertThat(tableExists(connection, "nim_removed_ids")).isFalse();

            assertThat(count(connection, "SELECT COUNT(*) FROM deployment WHERE id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics WHERE deployment_id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM component_removal WHERE id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_aud WHERE id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics_aud WHERE deployment_id = '" + NIM_ID + "'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM audit_activity WHERE resource_type = 'NimDeployment'")).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM disposable_resource WHERE group_id = '" + NIM_ID + "'"))
                    .as("only the NIM_SERVICE reference is deleted; the deployment's Secret stays for the cleaner")
                    .isEqualTo(1);

            assertThat(count(connection, "SELECT COUNT(*) FROM deployment WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM mcp_deployment WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics WHERE deployment_id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM component_removal WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_aud WHERE id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM deployment_topics_aud WHERE deployment_id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM audit_activity WHERE resource_type = 'McpDeployment'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM disposable_resource WHERE group_id = '" + MCP_ID + "'")).isEqualTo(1);
            assertThat(count(connection, "SELECT COUNT(*) FROM revinfo")).isEqualTo(1);
        }
    }

    private Flyway flyway(DataSource dataSource, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/" + vendor())
                .baselineOnMigrate(true)
                .baselineVersion("1.1")
                .target(target)
                .load();
    }

    private void seed(Connection connection) throws SQLException {
        for (String id : new String[] {NIM_ID, MCP_ID}) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO deployment (id, display_name, created_at_ms, updated_at_ms, allowed_domains, service_name) "
                            + "VALUES (?, ?, 1, 1, " + jsonParam() + ", ?)")) {
                ps.setString(1, id);
                ps.setString(2, id);
                ps.setString(3, "[]");
                ps.setString(4, id + "-svc");
                ps.executeUpdate();
            }
            execute(connection, "INSERT INTO deployment_topics (deployment_id, topic_name) VALUES ('" + id + "', 'topic')");
            execute(connection, "INSERT INTO component_removal (id, type, created_at) VALUES ('" + id + "', 'DEPLOYMENT', CURRENT_TIMESTAMP)");
        }
        execute(connection, "INSERT INTO nim_deployment (id, container_grpc_port, storage_size) VALUES ('" + NIM_ID + "', 8001, '20Gi')");
        execute(connection, "INSERT INTO mcp_deployment (id, transport) VALUES ('" + MCP_ID + "', 'SSE')");

        execute(connection, "INSERT INTO revinfo (timestamp) VALUES (1)");
        int rev = count(connection, "SELECT MAX(id) FROM revinfo");
        for (String id : new String[] {NIM_ID, MCP_ID}) {
            execute(connection, "INSERT INTO deployment_aud (id, rev, revtype, display_name) VALUES ('" + id + "', " + rev + ", 0, '" + id + "')");
            execute(connection, "INSERT INTO deployment_topics_aud (rev, revtype, deployment_id, topic_name) VALUES (" + rev + ", 0, '" + id + "', 'topic')");
        }
        execute(connection, "INSERT INTO nim_deployment_aud (id, rev) VALUES ('" + NIM_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO mcp_deployment_aud (id, rev) VALUES ('" + MCP_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO audit_activity (activity_id, activity_type, resource_type, resource_id, revision) "
                + "VALUES ('act-nim', 'CREATE', 'NimDeployment', '" + NIM_ID + "', " + rev + ")");
        execute(connection, "INSERT INTO audit_activity (activity_id, activity_type, resource_type, resource_id, revision) "
                + "VALUES ('act-mcp', 'CREATE', 'McpDeployment', '" + MCP_ID + "', " + rev + ")");

        insertDisposable(connection, NIM_ID, "NIM_SERVICE", "nim-legacy-svc");
        insertDisposable(connection, NIM_ID, "SECRET", "nim-legacy-secret");
        insertDisposable(connection, MCP_ID, "KNATIVE_SERVICE", "mcp-keep-svc");
    }

    private void insertDisposable(Connection connection, String groupId, String kind, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO disposable_resource (group_id, resource_reference, lifecycle_state, created_at) "
                        + "VALUES (?, " + jsonParam() + ", 'TO_CLEANUP', CURRENT_TIMESTAMP)")) {
            ps.setString(1, groupId);
            ps.setString(2, "{\"$type\":\"k8s\",\"namespace\":\"default\",\"kind\":\"" + kind + "\",\"name\":\"" + name + "\"}");
            ps.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        var metaData = connection.getMetaData();
        for (String candidate : new String[] {table, table.toUpperCase(java.util.Locale.ROOT)}) {
            try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, candidate, new String[] {"TABLE"})) {
                if (rs.next()) {
                    return true;
                }
            }
        }
        return false;
    }
}
```

Notes for the implementer: `disposable_resource.group_id` is `VARCHAR(36)` since V1.42, so string ids are valid. `revinfo.id` is an identity column on all vendors — do not insert it explicitly (SQL Server would need `IDENTITY_INSERT`). `service_name` values are distinct because the unique index on SQL Server only tolerates multiple `NULL`s via a filtered index. If `activity_type` turns out to be constrained to specific values on some vendor, use an existing `ActivityType` enum name from `model/audit`. Replace the fully-qualified `java.util.Locale` with an import to satisfy checkstyle.

- [ ] **Step 2: Wire the test into each vendor suite**

`H2FunctionalTests.java` — add (imports: `org.h2.jdbcx.JdbcDataSource`, `javax.sql.DataSource`, `java.util.UUID`, `...functional.tests.NimRemovalMigrationFunctionalTest`):

```java
    @Nested
    class NimRemovalMigrationTests extends NimRemovalMigrationFunctionalTest {
        @Override
        protected DataSource freshDataSource() {
            var dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:nim_removal_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            return dataSource;
        }

        @Override
        protected String vendor() {
            return "H2";
        }

        @Override
        protected String jsonParam() {
            return "? FORMAT JSON";
        }
    }
```

`PostgresFunctionalTests.java` — add (imports: `org.springframework.jdbc.datasource.DriverManagerDataSource`, `java.sql.DriverManager`, `java.sql.SQLException`, `javax.sql.DataSource`, `java.util.UUID`, the test class):

```java
    @Nested
    class NimRemovalMigrationTests extends NimRemovalMigrationFunctionalTest {
        @Override
        protected DataSource freshDataSource() throws SQLException {
            String dbName = "nim_removal_" + UUID.randomUUID().toString().replace("-", "");
            try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + dbName);
            }
            String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + dbName);
            return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        }

        @Override
        protected String vendor() {
            return "POSTGRES";
        }

        @Override
        protected String jsonParam() {
            return "CAST(? AS jsonb)";
        }
    }
```

`SqlServerFunctionalTests.java` — add a nested class mirroring the existing static `CREATE DATABASE` block (same collation, which the app relies on):

```java
    @Nested
    class NimRemovalMigrationTests extends NimRemovalMigrationFunctionalTest {
        @Override
        protected DataSource freshDataSource() throws SQLException {
            String dbName = "nim_removal_" + UUID.randomUUID().toString().replace("-", "");
            try (var connection = DriverManager.getConnection(MS_SQL_SERVER.getJdbcUrl(), MS_SQL_SERVER.getUsername(), MS_SQL_SERVER.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE [%s] COLLATE SQL_Latin1_General_CP1_CS_AS;".formatted(dbName));
            }
            return new DriverManagerDataSource(MS_SQL_SERVER.getJdbcUrl() + ";databaseName=" + dbName,
                    MS_SQL_SERVER.getUsername(), MS_SQL_SERVER.getPassword());
        }

        @Override
        protected String vendor() {
            return "MS_SQL_SERVER";
        }

        @Override
        protected String jsonParam() {
            return "?";
        }
    }
```

- [ ] **Step 3: Run the H2 migration test to verify it fails**

Run: `./gradlew testFast --tests "com.epam.aidial.deployment.manager.functional.H2FunctionalTests" > build/t4.log 2>&1; echo exit=$?`
Expected: FAIL in `NimRemovalMigrationTests` — `tableExists(connection, "nim_deployment")` is true (no V1.60 yet). Seeding itself must succeed; if it fails, fix the seed SQL before writing the migration.

- [ ] **Step 4: Write the migrations**

`src/main/resources/db/migration/H2/V1.60__RemoveNimDeployment.sql`:

```sql
-- NIM deployment support was removed in 0.22.0. Delete every row that belongs to a NIM deployment and drop the NIM tables.

-- Activity feed and audit trail
DELETE FROM audit_activity WHERE resource_type = 'NimDeployment';
DELETE FROM deployment_topics_aud WHERE deployment_id IN (SELECT id FROM nim_deployment_aud);
DELETE FROM deployment_aud WHERE id IN (SELECT id FROM nim_deployment_aud);
DROP TABLE nim_deployment_aud;

-- Live rows. nim_deployment.id -> deployment.id is ON DELETE RESTRICT and the FK name differs per vendor,
-- so stage the ids, drop the subtype table (its FK goes with it), then delete the base rows.
CREATE TABLE nim_removed_ids (id VARCHAR(36) NOT NULL PRIMARY KEY);
INSERT INTO nim_removed_ids (id) SELECT id FROM nim_deployment;
DROP TABLE nim_deployment;
DELETE FROM deployment_topics WHERE deployment_id IN (SELECT id FROM nim_removed_ids);
DELETE FROM component_removal WHERE type = 'DEPLOYMENT' AND id IN (SELECT id FROM nim_removed_ids);
DELETE FROM deployment WHERE id IN (SELECT id FROM nim_removed_ids);
DROP TABLE nim_removed_ids;

-- Cleanup queue: NIM_SERVICE is no longer a known K8sResourceKind and would fail deserialization in the cleaner.
DELETE FROM disposable_resource WHERE CAST(resource_reference AS VARCHAR) LIKE '%NIM_SERVICE%';
```

`src/main/resources/db/migration/POSTGRES/V1.60__RemoveNimDeployment.sql`: identical, except the last statement is

```sql
DELETE FROM disposable_resource WHERE resource_reference::text LIKE '%NIM_SERVICE%';
```

`src/main/resources/db/migration/MS_SQL_SERVER/V1.60__RemoveNimDeployment.sql`: identical to H2, except the last statement is

```sql
DELETE FROM disposable_resource WHERE resource_reference LIKE '%NIM_SERVICE%';
```

(`resource_reference` is `VARCHAR(MAX)` on SQL Server.)

- [ ] **Step 5: Run the H2 tests**

Run: `./gradlew testFast > build/t4.log 2>&1; echo exit=$?`
Expected: exit=0 — the migration test passes and the whole H2 functional suite boots against the migrated schema (`ddl-auto: validate`).

- [ ] **Step 6: Run Postgres and SQL Server suites** (requires Docker)

Run: `./gradlew test --tests "com.epam.aidial.deployment.manager.functional.PostgresFunctionalTests" --tests "com.epam.aidial.deployment.manager.functional.SqlServerFunctionalTests" > build/t4-db.log 2>&1; echo exit=$?`
Expected: exit=0. If Docker is unavailable, stop and report that these suites were not run — do not mark the task done.

- [ ] **Step 7: Regenerate the schema doc**

If the PostToolUse hook did not already update it: `./gradlew generateDbSchema > build/t4-schema.log 2>&1; echo exit=$?`. Verify `git diff docs/db-schema.md` removes exactly the `NIM_DEPLOYMENT` and `NIM_DEPLOYMENT_AUD` sections and TOC lines.

- [ ] **Step 8: Update the database spec** — in `specs/database-and-migrations/spec.md`, if it states a "current schema" version, set it to `V1.60`; update the matching cell in `specs/README.md` (`current schema V1.46`) to `V1.60`.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/db/migration/*/V1.60__RemoveNimDeployment.sql \
        src/test/java/com/epam/aidial/deployment/manager/functional \
        docs/db-schema.md specs/database-and-migrations/spec.md specs/README.md
git commit -m "feat(db): add V1.60 migration removing NIM deployment data and tables

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Docs, specs, changelog, upgrade plan and final audit

**Files:**
- Modify: `specs/nim-deployments/spec.md` (tombstone), `specs/README.md`, `README.md`, `CLAUDE.md`, `.specify/memory/constitution.md`, `src/main/java/com/epam/aidial/deployment/manager/service/CLAUDE.md`, `src/main/java/com/epam/aidial/deployment/manager/kubernetes/CLAUDE.md`, `docs/configuration.md`, `docs/deployment_guide_full_security_model.md`, `docs/diagramms/infrastructure_component/infrastrucutre-component-diagramm.drawio`, `docs/INFRA-CHANGELOG.md`
- Create: `docs/upgrade-plans/0.22.0.md`

**Interfaces:**
- Consumes: final behaviour from Tasks 1–4.
- Produces: no code.

- [ ] **Step 1: Tombstone the NIM capability spec** — replace the full content of `specs/nim-deployments/spec.md` with:

```markdown
# NIM Deployments

Status: **Removed** (0.22.0)

NVIDIA NIM deployment support (deployment type `NIM`, source type `ngc_registry`, the `NIMService` CRD integration and all `K8S_NIM_*` / `NIM_SERVICE_*` configuration) was removed in 0.22.0 because NIM models are no longer used. Migration `V1.60__RemoveNimDeployment` deletes existing NIM deployments together with their topics, audit history and activity feed entries, and drops the `nim_deployment` / `nim_deployment_aud` tables. Export archives from earlier releases still import; their `nimDeployments` block is ignored.

Design: `docs/superpowers/specs/2026-10-13-remove-nim-design.md`. Upgrade steps: `docs/upgrade-plans/0.22.0.md`.

Historical feature specs: [012-nim-url-schema](../012-nim-url-schema/spec.md), [013-nim-served-model-name](../013-nim-served-model-name/spec.md), [015-nim-kserve-migration](../015-nim-kserve-migration/spec.md).
```

- [ ] **Step 2: `specs/README.md`** — change the `nim-deployments` row to:

```markdown
| [nim-deployments](nim-deployments/spec.md) | Removed (0.22.0) | NVIDIA NIM model deployment — removed; tombstone links to historical specs |
```

and the `kubernetes-manifests` row summary to `Kubernetes manifest generation — KNative (serverless), KServe (model serving); env var injection; probe converters`. Leave the `012`/`013`/`015` rows unchanged (history).

- [ ] **Step 3: Contributor docs**
  - `CLAUDE.md` line ~64: replace the example slug `nim-deployments` with `inference-deployments`.
  - `.specify/memory/constitution.md` line ~54: `→ kubernetes (informer / knative / nim / kserve / event)` → `→ kubernetes (informer / knative / kserve / event)`.
  - `service/CLAUDE.md` line ~26: drop `, nim` from the deployment list; line ~27: `KNative / KServe / NIM manifest generation` → `KNative / KServe manifest generation`.
  - `kubernetes/CLAUDE.md`: delete the table row `| `nim/` | NVIDIA NIM CRD (`NIMService`) client |`; line ~28 → `- `specs/inference-deployments/spec.md` for KServe specifics.`
  - `README.md`: delete the feature bullet `- NIM support for simplified inference deployment`, the TOC entry `- [Deploying NVIDIA NIM Models](#deploying-nvidia-nim-models)`, and the whole `## Deploying NVIDIA NIM Models` section (up to the next `## ` heading).

- [ ] **Step 4: Ops docs**
  - `docs/configuration.md`: in the intro (lines ~16, ~20) replace "Knative and NIM service specifications" / "Knative/NIM service configuration" with "Knative and KServe service specifications" / "Knative/KServe service configuration"; in the progress-deadline section (~158–174) drop the NIM clause from the annotation sentence (keep KNative and KServe), "(KServe and NIM only)" → "(KServe only)", "KServe's or NIM's default" → "KServe's default"; delete the whole `#### NIM (NVIDIA Inference Microservices) Configuration` subsection (table rows `app.nim.*` and `app.nim-service-expose-ingress-config...`); delete the whole `#### NIM Service Configuration` subsection; delete the `app.validation.resources.max-storage-size` row; in the metrics paragraph (~233) change "(MCP/adapter/application/interceptor and **NIM**) the serving and operational blocks are reported unavailable with a reason, so NIM model metrics are not supported" → "(MCP/adapter/application/interceptor) the serving and operational blocks are reported unavailable with a reason"; in the node-pools row (~281) "for NIM and KServe-Inference deployments" → "for KServe-Inference deployments"; in the RBAC section (~756–830) delete the NIM namespace bullets and the `# NIM namespace Role (NVIDIA NIMService CRD)` YAML block.
  - **Note** on line ~227: the KServe `informer-resync-interval` row lists env var `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC`, but `application.yml` actually binds `K8S_KSERVE_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` (default 600). Fix the row to the real name and default.
  - `docs/deployment_guide_full_security_model.md`: delete the NIM row in the components table (~44), the NIM Operator prerequisite bullet (~54), the `# NIM namespace` Role + RoleBinding YAML (~278–330), `<K8S_NIM_DEPLOYMENT_NAMESPACE>` from the namespace lists (~387, ~394, ~423), and the `K8S_NIM_ENABLED` / `K8S_NIM_DEPLOYMENT_NAMESPACE` rows (~449–450).
  - `docs/diagramms/infrastructure_component/infrastrucutre-component-diagramm.drawio`: delete the two cells whose `value` is `Namespace: K8S_NIM_DEPLOYMENT_NAMESPACE (self hosted NIM models)` and `NIM Models Serving` (and any edge whose `source`/`target` references their ids); in the lifecycle text cell change `(Knative/NIM/KServe services, secrets)` → `(Knative/KServe services, secrets)`. Open the file afterwards and confirm it is still valid XML (`python -c "import xml.dom.minidom,sys; xml.dom.minidom.parse(sys.argv[1])" <path>`).

- [ ] **Step 5: `docs/INFRA-CHANGELOG.md`** — add at the top, above `## 0.21.0`:

```markdown
## 0.22.0

### Removed

#### NIM Deployments

NVIDIA NIM deployment support was removed. The following variables are no longer read and can be deleted from deployments (leaving them set is harmless):

- `K8S_NIM_ENABLED`
- `K8S_NIM_DEPLOYMENT_NAMESPACE`
- `K8S_NIM_DEPLOYMENT_STARTUP_TIMEOUT_SEC`
- `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC`
- `K8S_NIM_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL`
- `K8S_NIM_CLUSTER_HOST`
- `K8S_NIM_DEPLOYMENT_URL_SCHEMA`
- `K8S_NIM_DEPLOYMENT_KSERVE_MODE_ENABLED`
- `K8S_NIM_INGRESS_CLASS_NAME`
- `NIM_SERVICE_NGC_SECRET`
- `NIM_SERVICE_NGC_AUTH_SECRET`
- `RESOURCES_STORAGE_MAX_SIZE` — only validated NIM PVC sizes

RBAC for `nimservices` (`apps.nvidia.com`) in the NIM namespace is no longer required.

---
```

- [ ] **Step 6: Create `docs/upgrade-plans/0.22.0.md`** — first read `docs/upgrade-plans/0.21.0.md` and mirror its heading structure; content:

```markdown
# Upgrade to 0.22.0

## Breaking changes

### NIM deployment support removed

- API: deployment type `NIM` (`"$type": "nim"`), source type `ngc_registry`, export component type `NIM_DEPLOYMENT`, and the `nimDeployments` field of the import preview response are removed. Requests using them now return **400**. The admin frontend must drop NIM from its deployment-type lists and filters before or together with this release.
- Data: on first start, migration `V1.60__RemoveNimDeployment` **permanently deletes** all NIM deployments, their topics, audit history and activity-feed entries, and drops the `nim_deployment` / `nim_deployment_aud` tables.
- Export archives created by earlier releases still import; their `nimDeployments` block is ignored.

## Before upgrading

The application no longer manages `NIMService` resources, so it cannot remove ones that still exist. Do **one** of the following on 0.21.x:

1. Undeploy every NIM deployment through the API/UI (this deletes its `NIMService` cleanly), **or**
2. After upgrading, delete leftovers manually:
   ```bash
   kubectl get nimservices -A
   kubectl delete nimservice <name> -n <namespace>
   ```
   and delete any Ingress objects that were created for NIM services in legacy (non-KServe) mode.

If you need the NIM deployment definitions for reference, export them via the config export on 0.21.x before upgrading.

## After upgrading

- Remove the variables listed under 0.22.0 → Removed in `docs/INFRA-CHANGELOG.md` (optional; they are ignored).
- Remove the `nimservices` Role/RoleBinding from the NIM namespace (optional).
- The NIM Operator and its CRD can be uninstalled if nothing else uses them.
```

- [ ] **Step 7: Final audit grep**

Run: `git grep -n -i -E "\bnim\b|nim_|_nim|nimservice|ngc_registry|NgcRegistry|K8S_NIM|NIM_SERVICE" -- . ':!specs/0*' ':!docs/upgrade-plans/0.1*' ':!docs/upgrade-plans/0.20.0.md' ':!docs/upgrade-plans/0.21.0.md' ':!docs/superpowers' ':!.specify/templates' ':!.specify/scripts' ':!.specify/extensions' ':!.claude/skills/speckit-*'`
Expected: every hit is in one of: `src/main/java/db/migration/**` and `src/main/resources/db/migration/**` (applied migrations + the new V1.60), `docs/INFRA-CHANGELOG.md` (past entries + the new Removed section), `docs/upgrade-plans/0.22.0.md`, `specs/nim-deployments/spec.md` (tombstone), `specs/README.md` (tombstone + historical rows), `specs/export-import/spec.md` (legacy-import scenario), `src/test/resources/config/expected_export_config_for_import.json` (legacy fixture), the stale flag in `H2FunctionalTests`, the regression tests from Tasks 2–3 and the migration test from Task 4. Anything else: remove it, or record why it stays.

- [ ] **Step 8: Full verification**

Run: `./gradlew clean build > build/t5.log 2>&1; echo exit=$?` (runs checkstyle and the full test suite incl. Testcontainers)
Expected: exit=0. If Docker is unavailable, run `./gradlew clean checkstyleMain checkstyleTest testFast` instead and state explicitly in the report that the Postgres/SQL Server suites were not run.

- [ ] **Step 9: Commit**

```bash
git add -A specs README.md CLAUDE.md .specify/memory/constitution.md docs src/main/java/com/epam/aidial/deployment/manager/service/CLAUDE.md src/main/java/com/epam/aidial/deployment/manager/kubernetes/CLAUDE.md
git commit -m "docs: retire NIM capability, add 0.22.0 upgrade plan and changelog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
