# Configuration

This document describes the configuration properties available for the AI DIAL Admin Deployment Manager Backend application.

## Overview

The application uses Spring Boot configuration with YAML format. Configuration properties can be set via:

- Environment variables
- Application properties/YAML files
- Command line arguments
- System properties

### Externalized configuration via SPRING_CONFIG_ADDITIONAL_LOCATION

You can point the application to one or more external YAML/Properties files using the `SPRING_CONFIG_ADDITIONAL_LOCATION` environment variable. Any property in this document (and any nested structures) can be overridden through this mechanism, including full Knative and NIM service specifications.

- Set `SPRING_CONFIG_ADDITIONAL_LOCATION` to a file or directory location (e.g., `file:/config/override.yml` or `file:/config/`).
- Values from these files are loaded by Spring and override defaults from `application.yml` and environment variables according to Spring’s property precedence.
- This allows you to change any Knative/NIM service configuration field, even ones not explicitly listed in the tables below (for example, `serviceAccountName`, `automountServiceAccountToken`, or additional annotations/labels).

Example minimal override (only serviceAccountName):

```yaml
app:
  knative-service-config:
    spec:
      template:
        spec:
          serviceAccountName: knative-service-account
```

## Configuration Properties

### Spring Framework Configuration

#### Application Configuration


| Property                  | Environment Variable | Default Value        | Required | Applied when | Description      |
| ------------------------- | -------------------- | -------------------- | -------- | ------------ | ---------------- |
| `spring.application.name` | -                    | `deployment-manager` | No       | -            | Application name |


#### Database Configuration (JPA/Hibernate)


| Property                                                          | Environment Variable | Default Value                     | Required | Applied when | Description                |
| ----------------------------------------------------------------- | -------------------- | --------------------------------- | -------- | ------------ | -------------------------- |
| `spring.jpa.database-platform`                                    | -                    | `org.hibernate.dialect.H2Dialect` | No       | -            | JPA database platform      |
| `spring.jpa.hibernate.ddl-auto`                                   | -                    | `validate`                        | No       | -            | Hibernate DDL auto mode    |
| `spring.jpa.show-sql`                                             | -                    | `false`                           | No       | -            | Enable SQL logging         |
| `spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access` | -                    | `true`                            | No       | -            | Allow JDBC metadata access |
| `spring.jpa.properties.org.hibernate.envers.store_data_at_delete` | -                    | `true`                            | No       | -            | Store full entity state on DELETE for audit trail reconstruction |


#### Database Migration (Flyway)


| Property                            | Environment Variable | Default Value                | Required | Applied when | Description                   |
| ----------------------------------- | -------------------- | ---------------------------- | -------- | ------------ | ----------------------------- |
| `spring.flyway.enabled`             | -                    | `true`                       | No       | -            | Enable Flyway migrations      |
| `spring.flyway.default-schema`      | -                    | `PUBLIC`                     | No       | -            | Default schema for migrations |
| `spring.flyway.locations`           | `DB_VENDOR`          | `classpath:db/migration/H2/` | No       | -            | Migration scripts location    |
| `spring.flyway.baseline-on-migrate` | -                    | `true`                       | No       | -            | Enable baseline on migrate    |
| `spring.flyway.baseline-version`    | -                    | `1.1`                        | No       | -            | Baseline version              |


#### MVC Configuration


| Property                           | Environment Variable | Default Value | Required | Applied when | Description                                        |
| ---------------------------------- | -------------------- | ------------- | -------- | ------------ | -------------------------------------------------- |
| `spring.mvc.async.request-timeout` | -                    | `600000`      | No       | -            | Async request timeout in milliseconds (10 minutes) |


### Application-Specific Configuration

#### Kubernetes Configuration


| Property                                             | Environment Variable | Default Value               | Required | Applied when               | Description                                       |
| ---------------------------------------------------- | -------------------- | --------------------------- | -------- | -------------------------- | ------------------------------------------------- |
| `app.kubernetes.connect-type`                        | `K8S_CONNECT_TYPE`   | `CONFIG_FILE`               | No       | -                          | Kubernetes connection type (CONFIG_FILE or TOKEN) |
| `app.kubernetes.config_file.kube-config`             | -                    | `${user.home}/.kube/config` | No       | `connect-type=CONFIG_FILE` | Path to kubeconfig file                           |
| `app.kubernetes.config_file.contexts.deploy-context` | `K8S_DEPLOY_CONTEXT` | -                           | No       | `connect-type=CONFIG_FILE` | Kubernetes deployment context                     |
| `app.kubernetes.token.master-url`                    | `K8S_MASTER_URL`     | -                           | Yes      | `connect-type=TOKEN`       | Kubernetes master URL for token authentication    |
| `app.kubernetes.token.oauth-token`                   | `K8S_OAUTH_TOKEN`    | -                           | Yes      | `connect-type=TOKEN`       | OAuth token for Kubernetes authentication         |


#### Docker Registry Configuration


| Property                                  | Environment Variable         | Default Value          | Required                                          | Applied when | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| ----------------------------------------- | ---------------------------- | ---------------------- | ------------------------------------------------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `app.registry.url`                        | `DOCKER_REGISTRY`            | `test-docker-registry` | No (recommended to adjust for your environment)   | -            | Docker registry URL. This is the registry where the service will publish built Docker images.                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `app.registry.protocol`                   | `DOCKER_REGISTRY_PROTOCOL`   | `https`                | No                                                | -            | Docker registry protocol                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `app.registry.auth`                       | `DOCKER_REGISTRY_AUTH`       | `NONE`                 | No (recommended to adjust for target environment) | -            | Docker registry authentication scheme (values: NONE, BASIC)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `app.registry.user`                       | `DOCKER_REGISTRY_USER`       | -                      | No                                                | `auth=BASIC` | Docker registry username                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `app.registry.password`                   | `DOCKER_REGISTRY_PASSWORD`   | -                      | No                                                | `auth=BASIC` | Docker registry password                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `app.registry.trusted-private-registries` | `TRUSTED_PRIVATE_REGISTRIES` | -                      | No                                                | -            | JSON array of registry configuration objects. Each object must have at least `"registry"` (the registry host), and may include `"authScheme"` (`"NONE"` or `"BASIC"`), `"protocol"` (`"https"` by default), `"user"`, and `"password"` (for `"BASIC"` auth). Example: `[{"registry":"my.private.registry","authScheme":"BASIC","user":"user1","password":"pass1"},{"registry":"another.registry","protocol":"http","authScheme":"NONE"}]` These are read-only registries from which the service is allowed to copy images into the `DOCKER_REGISTRY`. |


#### Git Configuration


| Property                        | Environment Variable        | Default Value | Required | Applied when | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ------------------------------- | --------------------------- | ------------- | -------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `app.git.trusted-private-repos` | `TRUSTED_PRIVATE_GIT_REPOS` | -             | No       | -            | JSON array of Git repository configuration objects. Each object must have at least `"host"` (the Git repository host), and may include `"protocol"` (`"https"` or `"http"`, default: `"https"`), `"user"`, `"password"`, `"token"`, `"sshKeyPath"`, and `"sshKnownHostsPath"`. The `"protocol"` field is only applicable for HTTPS/HTTP authentication (when using `"user"`/`"password"` or `"token"`), and is not applicable when using SSH key authentication (`"sshKeyPath"`). For SSH authentication, `"sshKeyPath"` and `"sshKnownHostsPath"` must be file paths (not file contents). The files are read at application startup and the application will fail to start if the files are not found. Example: `[{"host":"git.example.com","protocol":"https","user":"user1","password":"pass1"},{"host":"git.private.com","sshKeyPath":"/path/to/id_rsa","sshKnownHostsPath":"/path/to/known_hosts"}]` These are trusted private Git repositories from which the service is allowed to clone code during build operations. |


#### LogReader Configuration


| Property                                 | Environment Variable             | Default Value | Required | Applied when | Description                           |
| ---------------------------------------- | -------------------------------- | ------------- | -------- | ------------ | ------------------------------------- |
| `app.pipeline.log-reader.max-log-count`  | `PIPELINE_RUNNER_MAX_LOG_COUNT`  | `10000`       | No       | -            | Maximum number of log entries to read |
| `app.pipeline.log-reader.max-log-length` | `PIPELINE_RUNNER_MAX_LOG_LENGTH` | `10000`       | No       | -            | Maximum length of log entries         |


#### Notification (SSE/Logs/Statuses) Configuration


| Property                     | Environment Variable | Default Value | Required | Applied when | Description                                  |
| ---------------------------- | -------------------- | ------------- | -------- | ------------ | -------------------------------------------- |
| `app.sse.heartbeat.interval` | -                    | `10000`       | No       | -            | SSE heartbeat interval in milliseconds       |
| `app.sse.poll-interval-ms`   | -                    | `1000`        | No       | -            | SSE streaming cycle interval in milliseconds |


#### Build and Deployment Configuration


| Property                                                     | Environment Variable                                     | Default Value                    | Required                                             | Applied when | Description                                                                                                                                                                                                                                                |
| ------------------------------------------------------------ | -------------------------------------------------------- | -------------------------------- | ---------------------------------------------------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `app.build-namespace`                                        | `K8S_BUILD_NAMESPACE`                                    | `default`                        | No (recommended to adjust for target environment)    | -            | Kubernetes namespace for build operations                                                                                                                                                                                                                  |
| `app.git-clone-image`                                        | `GIT_CLONE_IMAGE`                                        | `alpine/git:latest`              | No (recommended to use a specific tag in production) | -            | Docker image for Git cloning in init containers. Must include git and openssh-client for SSH support.                                                                                                                                                      |
| `app.builder-rootless-image`                                 | -                                                        | `moby/buildkit:v0.27.1-rootless` | No (recommended to use a specific tag in production) | -            | Buildkit rootless image for building containers.                                                                                                                                                                                                           |
| `app.builder-root-image`                                     | -                                                        | `moby/buildkit:v0.27.1`          | No (recommended to use a specific tag in production) | -            | Buildkit image for building containers. The root user is used to cover cases where rootless limitations prevent image building, but the admin is confident in the reliability and security of the built image.                                             |
| `app.analyser-image`                                         | -                                                        | `anchore/syft:latest`            | No (recommended to use a specific tag in production) | -            | Docker image used for analyzing container images                                                                                                                                                                                                           |
| `app.copy-image`                                             | -                                                        | `quay.io/skopeo/stable:latest`   | No (recommended to use a specific tag in production) | -            | Docker image for copying images                                                                                                                                                                                                                            |
| `app.docker-config-path`                                     | -                                                        | `/kaniko/.docker/config.json`    | No                                                   | -            | Path to the location where the Docker config file is mounted for build containers.                                                                                                                                                                         |
| `app.cilium-network-policies-enabled`                        | `CILIUM_NETWORK_POLICIES_ENABLED`                        | `false`                          | No                                                   | -            | Flag that allows to enable Cilium network policies for image build and deployments.                                                                                                                                                                        |
| `app.image-name-format`                                      | `IMAGE_NAME_FORMAT`                                      | `app-%s`                         | No                                                   | -            | Name format for images that are built using Deployment Manager. Must contain `%s` that will be replaced by image definition ID.                                                                                                                            |
| `app.resource-name-prefix`                                   | `RESOURCE_NAME_PREFIX`                                   | -                                | No                                                   | -            | Prefix that will be added to all resources that image build and deployments produce. Important note: do not change this value on exising setups, otherwise existing images and K8s resources will be lost.                                                 |
| `app.deployment.healthcheck-enabled`                         | `DEPLOYMENT_HEALTHCHECK_ENABLED`                         | `true`                           | No                                                   | -            | Flag that allows to enable/disable deployment healthchecks                                                                                                                                                                                                 |
| `app.deployment.progress-deadline.default-initial-delay`     | `DEPLOYMENT_PROGRESS_DEADLINE_DEFAULT_INITIAL_DELAY`     | `0`                              | No                                                   | -            | Default `initialDelaySeconds` used when computing the KNative progress deadline from a startup probe that has no explicit initial delay set. See [Progress Deadline](#progress-deadline-configuration).                                                    |
| `app.deployment.progress-deadline.default-period`            | `DEPLOYMENT_PROGRESS_DEADLINE_DEFAULT_PERIOD`            | `10`                             | No                                                   | -            | Default `periodSeconds` used when computing the KNative progress deadline from a startup probe that has no explicit period set. See [Progress Deadline](#progress-deadline-configuration).                                                                 |
| `app.deployment.progress-deadline.default-failure-threshold` | `DEPLOYMENT_PROGRESS_DEADLINE_DEFAULT_FAILURE_THRESHOLD` | `3`                              | No                                                   | -            | Default `failureThreshold` used when computing the KNative progress deadline from a startup probe that has no explicit failure threshold set. See [Progress Deadline](#progress-deadline-configuration).                                                   |
| `app.deployment.progress-deadline.buffer-seconds`            | `DEPLOYMENT_PROGRESS_DEADLINE_BUFFER_SECONDS`            | `30`                             | No                                                   | -            | Extra buffer (in seconds) added on top of the computed startup probe window when setting the KNative progress deadline. Accounts for image pull time, readiness probe, and scheduling overhead. See [Progress Deadline](#progress-deadline-configuration). |


#### Progress Deadline Configuration

KNative has a default progress deadline of 600 seconds (10 minutes). If a Revision does not become ready within this window, KNative marks it as **Failed** and terminates the pod. This can cause deployments of large models to fail if they need more time to download or initialize.

The application automatically sets the `serving.knative.dev/progress-deadline` annotation on the Revision template (for KNative, when a startup probe is configured) or the service metadata (for KServe and NIM):

**When a startup probe is configured**, the deadline is computed from the probe parameters:

```
progressDeadline = initialDelaySeconds + ((failureThreshold - 1) × periodSeconds) + timeoutSeconds + bufferSeconds
```

If the startup probe does not specify `initialDelaySeconds`, `periodSeconds`, or `failureThreshold`, the corresponding default values from the properties above are used (matching Kubernetes defaults).

**When no startup probe is configured** (KServe and NIM only), the deadline falls back to the deployment type's `startup-timeout` value plus `buffer-seconds`. This ensures that the Kubernetes-level timeout aligns with the application-level health check timeout:

```
progressDeadline = startupTimeout + bufferSeconds
```

For example, with KServe's or NIM's default `startup-timeout` of 3600s and `buffer-seconds` of 30, the progress deadline will be `3630s`.

For KNative deployments (MCP servers, interceptors, adapters, applications), when no startup probe is configured, no annotation is set and KNative's built-in default of 600s applies.

#### MCP Proxy Configuration


| Property                            | Environment Variable                | Default Value | Required | Applied when | Description                                                                                                                              |
| ----------------------------------- | ----------------------------------- | ------------- | -------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `app.build.mcp-proxy.images.alpine` | `MCP_PROXY_EXECUTABLE_IMAGE_ALPINE` | -             | Yes      | -            | Docker image name for Alpine-based MCP proxy executable. This image is built using the `Dockerfile` located in the `docs/alpine` folder. |
| `app.build.mcp-proxy.images.debian` | `MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN` | -             | Yes      | -            | Docker image name for Debian-based MCP proxy executable. This image is built using the `Dockerfile` located in the `docs/debian` folder. |


> Build once and push to your registry (required before first use):

```bash
# Alpine-based MCP proxy executable image
docker buildx build --push -f docs/alpine/Dockerfile -t <your-registry>/mcp-proxy-exec:alpine .

# Debian-based MCP proxy executable image
docker buildx build --push -f docs/debian/Dockerfile -t <your-registry>/mcp-proxy-exec:debian .
```

Set `app.build.mcp-proxy.images.alpine` and `app.build.mcp-proxy.images.debian` (or `MCP_PROXY_EXECUTABLE_IMAGE_ALPINE` / `MCP_PROXY_EXECUTABLE_IMAGE_DEBIAN`) to the pushed image references above.

#### Knative Configuration


| Property                                      | Environment Variable                                  | Default Value | Required                                          | Applied when | Description                                                            |
| --------------------------------------------- | ----------------------------------------------------- | ------------- | ------------------------------------------------- | ------------ | ---------------------------------------------------------------------- |
| `app.knative.enabled`                         | `K8S_KNATIVE_ENABLED`                                 | `true`        | No                                                | -            | Enable or disable Knative deployment support                           |
| `app.knative.deploy.namespace`                | `K8S_KNATIVE_DEPLOYMENT_NAMESPACE`                    | `default`     | No (recommended to adjust for target environment) | -            | Kubernetes namespace for Knative deployments                           |
| `app.knative.deploy.startup-timeout`          | `K8S_KNATIVE_DEPLOYMENT_STARTUP_TIMEOUT_SEC`          | `300`         | No                                                | -            | Knative service startup timeout in seconds                             |
| `app.knative.deploy.undeploy-timeout`         | `K8S_KNATIVE_DEPLOYMENT_UNDEPLOY_TIMEOUT_SEC`         | `300`         | No                                                | -            | Knative service undeploy timeout in seconds                            |
| `app.knative.deploy.informer-resync-interval` | `K8S_KNATIVE_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` | `60`          | No                                                | -            | Kubernetes informer resync interval in seconds for Knative deployments |
| `app.knative.deploy.ready-grace-period`       | `K8S_KNATIVE_DEPLOYMENT_READY_GRACE_PERIOD_SEC`       | `15`          | No                                                | -            | Grace period in seconds after Ready=False before reporting CRASHED status. Set to 0 to disable |


#### NIM (NVIDIA Inference Microservices) Configuration


| Property                                  | Environment Variable                              | Default Value | Required                                          | Applied when | Description                                                                                                                                                                                                             |
|-------------------------------------------|---------------------------------------------------|---------------|---------------------------------------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app.nim.enabled`                         | `K8S_NIM_ENABLED`                                 | `false`       | No                                                | -            | Enable or disable NIM deployment support                                                                                                                                                                                |
| `app.nim.deploy.namespace`                | `K8S_NIM_DEPLOYMENT_NAMESPACE`                    | `default`     | No (recommended to adjust for target environment) | -            | Kubernetes namespace for NIM deployments                                                                                                                                                                                |
| `app.nim.deploy.startup-timeout`          | `K8S_NIM_DEPLOYMENT_STARTUP_TIMEOUT_SEC`          | `3600`        | No                                                | -            | NIM service startup timeout in seconds (1 hour). Used as the fallback progress deadline when no startup probe is configured. See [Progress Deadline](#progress-deadline-configuration).                                 |
| `app.nim.deploy.informer-resync-interval` | `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` | `60`          | No                                                | -            | Kubernetes informer resync interval in seconds for NIM deployments                                                                                                                                                      |
| `app.nim.deploy.use-cluster-internal-url` | `K8S_NIM_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL`     | `true`        | No (recommended to adjust for target environment) | -            | When `true`, NIM services use cluster-internal URL. When `false`, external URL is used.                                                                            |
| `app.nim.deploy.cluster-host`             | `K8S_NIM_CLUSTER_HOST`                            | -             | Yes, when `use-cluster-internal-url=false` and `kserve-mode-enabled=false` | NIM deploy | Cluster host used to construct the ingress host for NIM services in legacy (standalone) mode. Unused when `kserve-mode-enabled=true`. |
| `app.nim.deploy.url-schema`               | `K8S_NIM_DEPLOYMENT_URL_SCHEMA`                   | -             | No                                                | NIM deploy   | Override URL schema prefix for resolved NIM service URLs. When empty, defaults apply: `http` for cluster-internal, `https` for external. Accepts values with or without `://` (e.g., both `https` and `https://` work). |
| `app.nim.deploy.kserve-mode-enabled`      | `K8S_NIM_DEPLOYMENT_KSERVE_MODE_ENABLED`          | `false`       | No                                                | NIM deploy   | When `true`, generates NIMService manifests with `inferencePlatform: kserve`, Knative `expose.router`, and Knative autoscaling annotations. When `false` (default), uses standalone mode with ingress-based external exposure. |
| `app.nim-service-expose-ingress-config.spec.ingressClassName` | `K8S_NIM_INGRESS_CLASS_NAME`                              | `nginx` | No | NIM deploy (legacy mode with external URL) | Ingress class name applied to the generated NIM Ingress resource (e.g., `nginx`, `traefik`).            |

#### KServe Configuration


| Property                                     | Environment Variable                              | Default Value | Required                                          | Applied when | Description                                                                                                                                                                                  |
|----------------------------------------------|---------------------------------------------------|---------------|---------------------------------------------------|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app.kserve.enabled`                         | `K8S_KSERVE_ENABLED`                              | `false`       | No                                                | -            | Enable or disable KServe deployment support                                                                                                                                                  |
| `app.kserve.deploy.namespace`                | `K8S_KSERVE_DEPLOYMENT_NAMESPACE`                 | `default`     | No (recommended to adjust for target environment) | -            | Kubernetes namespace for KServe deployments (max 14 chars due to [https://github.com/kserve/kserve/issues/4807](https://github.com/kserve/kserve/issues/4807)).                              |
| `app.kserve.deploy.startup-timeout`          | `K8S_KSERVE_DEPLOYMENT_STARTUP_TIMEOUT_SEC`       | `3600`        | No                                                | -            | Seconds to wait for a KServe service to become ready. Used as the fallback progress deadline when no startup probe is configured. See [Progress Deadline](#progress-deadline-configuration). |
| `app.kserve.deploy.informer-resync-interval` | `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` | `60`          | No                                                | -            | Kubernetes informer resync interval in seconds for KServe deployments                                                                                                                        |
| `app.kserve.deploy.use-cluster-internal-url` | `K8S_KSERVE_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL`  | `true`        | No (recommended to adjust for target environment) | -            | Whether to use cluster-internal URL for KServe services.                                                                                                                                     |


#### Text-Classification Transformer Configuration

Applies to chained text-classification inference deployments (auto-detected from HuggingFace metadata — see `specs/inference-deployments/spec.md`). The image property has no default and **must be set** before any chained deployment can be deployed; resource defaults are sized for SST-2-class transformer workloads (~200MB image, stateless).

| Property                                                                | Environment Variable                                              | Default Value | Required                                          | Applied when                                | Description                                                                                                                  |
|-------------------------------------------------------------------------|-------------------------------------------------------------------|---------------|---------------------------------------------------|---------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `app.inference.text-classification-transformer.name`                              | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_NAME`                              | `kserve-container` | No                                                | Chained manifest generation                 | Container name for the chained transformer block.                                                                            |
| `app.inference.text-classification-transformer.image`                             | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE`                             | -                  | Yes for chained deployments                       | Deploying an inference whose model is detected as text-classification | Container image for the chained text-classification transformer. Deploy fails fast with HTTP 5xx if unset for chained mode. |
| `app.inference.text-classification-transformer.image-pull-policy`                 | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_IMAGE_PULL_POLICY`                 | `IfNotPresent`     | No                                                | Chained manifest generation                 | Image pull policy applied to the transformer container.                                                                      |
| `app.inference.text-classification-transformer.resources.requests.cpu`            | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_CPU_REQUEST`                       | `100m`             | No                                                | Chained manifest generation                 | CPU request for the transformer container.                                                                                   |
| `app.inference.text-classification-transformer.resources.requests.memory`         | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_MEMORY_REQUEST`                    | `256Mi`            | No                                                | Chained manifest generation                 | Memory request for the transformer container.                                                                                |
| `app.inference.text-classification-transformer.resources.requests.ephemeral-storage` | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_EPHEMERAL_STORAGE_REQUEST`      | `500M`             | No                                                | Chained manifest generation                 | Ephemeral-storage request for the transformer container.                                                                     |
| `app.inference.text-classification-transformer.resources.limits.cpu`              | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_CPU_LIMIT`                         | `500m`             | No                                                | Chained manifest generation                 | CPU limit for the transformer container.                                                                                     |
| `app.inference.text-classification-transformer.resources.limits.memory`           | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_MEMORY_LIMIT`                      | `512Mi`            | No                                                | Chained manifest generation                 | Memory limit for the transformer container.                                                                                  |
| `app.inference.text-classification-transformer.resources.limits.ephemeral-storage` | `INFERENCE_TEXT_CLASSIFICATION_TRANSFORMER_EPHEMERAL_STORAGE_LIMIT`          | `1G`               | No                                                | Chained manifest generation                 | Ephemeral-storage limit for the transformer container.                                                                       |


#### [Preview] Node Pool Configuration

Node pools are scheduling presets. Each pool projects its scheduling primitives onto the workload's pod template at deploy time.

Each pool entry has the following fields:

- `id` — **required, immutable** machine identifier. Deployments persist this value (column `deployment.node_pool`) and resolve their pool by id at deploy time. **Renaming an `id` breaks every deployment that referenced the old value.** Treat it as a primary key. Must be unique across pools.
- `name` — **required** human-readable display label shown on the UI. **Safe to change at any time** — deployments are not affected because they reference the pool by id, not name. The current `name` is resolved from configuration at read time and exposed on deployment responses as `nodePoolName`. Recommended unique but not enforced.
- `description` — optional free-form text shown alongside the pool in the UI. No functional meaning.
- `nodeSelector` — optional map of label key/value pairs. Applied verbatim to the pod template's `nodeSelector`; an existing template value is overwritten.
- `affinity` — optional full Kubernetes `Affinity` object (`nodeAffinity`, `podAffinity`, `podAntiAffinity`). Validated against the Kubernetes schema at startup. Applied verbatim to the pod template; an existing template value is overwritten.
- `tolerations` — optional list of Kubernetes `Toleration` objects. Appended to the pod template's existing tolerations (not replaced).

| Property | Environment Variable | Default | Required | Description |
|----------|---------------------|---------|----------|-------------|
| `app.node-pools.pools` | `NODE_POOLS` [Preview] | _(empty)_ | No | YAML list of pool entries. Per-entry field schema described above. |
| `app.node-pools.default` | `NODE_POOL_DEFAULT` [Preview] | _(empty)_ | No | Catch-all default **pool id**. Stamped onto deployments created without an explicit `nodePoolId`. Must match an `id` in `NODE_POOLS`. |
| `app.node-pools.default-model` | `NODE_POOL_DEFAULT_MODEL` [Preview] | _(empty)_ | No | Model-workload default **pool id**. Takes precedence over `NODE_POOL_DEFAULT` for NIM and KServe-Inference deployments. Must match an `id` in `NODE_POOLS`. |

Defaults are stamped at create time and persisted on the record; updates never re-run the cascade. Operators may freely rename a pool's `name` afterwards — the FE will start showing the new label on the next deployment read.

**Operator note — admin-edit asymmetry**: edits to a pool's `nodeSelector` / `affinity` / `tolerations` are picked up by every existing deployment that references the pool **automatically on the next redeploy** — pool primitives are re-tunable. In contrast, changes to `NODE_POOL_DEFAULT` or `NODE_POOL_DEFAULT_MODEL` apply only to deployments **created after the change**; existing deployments retain whichever pool id was stamped at their creation time. To migrate an existing deployment to a new pool, edit its `nodePoolId` field via the standard update endpoint — there is no batch migration. Pool identity (`id`) is immutable by design; pool contents are mutable. The legacy `NODE_POOL_LABEL_KEY` env variable is ignored if still set — it has no effect on scheduling.

**Example**:

```bash
NODE_POOLS=$(cat <<'YAML'
- id: cpu-pool
  name: CPU pool
  description: General-purpose CPU workloads
  nodeSelector:
    workload: cpu
- id: gpu-pool
  name: GPU pool
  description: Inference and fine-tuning on A100/H100
  affinity:
    nodeAffinity:
      requiredDuringSchedulingIgnoredDuringExecution:
        nodeSelectorTerms:
        - matchExpressions:
          - key: accelerator-type
            operator: In
            values: [nvidia-a100, nvidia-h100]
  tolerations:
  - key: dedicated
    operator: Equal
    value: gpu
    effect: NoSchedule
YAML
)
NODE_POOL_DEFAULT=cpu-pool
NODE_POOL_DEFAULT_MODEL=gpu-pool
```

#### Cleanup and Maintenance Configuration


| Property                                           | Environment Variable                           | Default Value | Required | Applied when | Description                                           |
| -------------------------------------------------- | ---------------------------------------------- | ------------- | -------- | ------------ | ----------------------------------------------------- |
| `app.resource-cleaner-cron`                        | `RESOURCE_CLEANER_CRON`                        | `0 0 5 * * *` | No       | -            | Cron expression for resource cleanup (daily at 5 AM)  |
| `app.resource-cleaner-take-size`                   | `RESOURCE_CLEANER_TAKE_SIZE`                   | `100`         | No       | -            | Batch size for resource cleanup operations            |
| `app.resource-cleaner-scheduler-lock-at-most-for`  | `RESOURCE_CLEANER_SCHEDULER_LOCK_AT_MOST_FOR`  | `10m`         | No       | -            | Maximum lock duration for resource cleaner            |
| `app.component-cleaner-cron`                       | `COMPONENT_CLEANER_CRON`                       | `0 0 5 * * *` | No       | -            | Cron expression for component cleanup (daily at 5 AM) |
| `app.component-cleaner-scheduler-lock-at-most-for` | `COMPONENT_CLEANER_SCHEDULER_LOCK_AT_MOST_FOR` | `10m`         | No       | -            | Maximum lock duration for component cleaner           |


#### Deployment State Synchronization Configuration


| Property                                               | Environment Variable                                   | Default Value             | Required | Applied when | Description                                                                                                                         |
| ------------------------------------------------------ | ------------------------------------------------------ | ------------------------- | -------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| `app.deployment.reconcile.background.cron`             | `DEPLOYMENT_RECONCILE_BACKGROUND_CRON`                 | `0 */15 * * * *`          | No       | -            | Cron expression for scheduled reconciliation of deployments in background                                                           |
| `app.deployment.reconcile.background.lock-at-most`     | `DEPLOYMENT_RECONCILE_BACKGROUND_LOCK_AT_MOST`         | `5m`                      | No       | -            | Maximum lock duration for background reconciliation                                                                                 |
| `app.deployment.reconcile.background.stale-threshold`  | `DEPLOYMENT_RECONCILE_BACKGROUND_STALE_THRESHOLD_MINS` | `10`                      | No       | -            | Maximum time allowed for deployment to stay in pending state until triggering a fallback status sync                                |
| `app.deployment.reconcile.startup.enabled`             | `DEPLOYMENT_RECONCILE_ON_STARTUP_ENABLED`              | `true`                    | No       | -            | Enable or disable deployment state synchronization process on app startup                                                           |
| `app.deployment.reconcile.startup.batch-size`          | `DEPLOYMENT_RECONCILE_ON_STARTUP_BATCH_SIZE`           | `50`                      | No       | -            | Number of deployments to process in a single batch during reconciliation on startup                                                 |
| `app.deployment.reconcile.startup.concurrency`         | `DEPLOYMENT_RECONCILE_ON_STARTUP_CONCURRENCY`          | `2`                       | No       | -            | Number of threads to use during deployment reconciliation on startup                                                                |
| `app.deployment.reconcile.executor.thread-pool-size`   | `DEPLOYMENT_RECONCILE_EXECUTOR_THREAD_POOL_SIZE`       | `5`                       | No       | -            | Number of threads in the reconciliation pool used by Kubernetes informer event handlers. Bounds concurrent reconciles to avoid OOM. |
| `app.deployment.reconcile.executor.queue-capacity`     | `DEPLOYMENT_RECONCILE_EXECUTOR_QUEUE_CAPACITY`         | `100`                     | No       | -            | Max pending reconcile tasks. When full, the caller runs the task (backpressure). Tune with thread-pool-size for resync bursts.      |
| `app.deployment.reconcile.executor.thread-name-prefix` | `DEPLOYMENT_RECONCILE_EXECUTOR_THREAD_NAME_PREFIX`     | `k8s-reconciliation-pool` | No       | -            | Thread name prefix for reconciliation pool threads.                                                                                 |


#### Knative Service Default Configuration


| Property                                                                                                | Environment Variable                    | Default Value | Required | Applied when | Description                        |
| ------------------------------------------------------------------------------------------------------- | --------------------------------------- | ------------- | -------- | ------------ | ---------------------------------- |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/initial-scale]` | `KNATIVE_SERVICE_DEFAULT_INITIAL_SCALE` | `1`           | No       | -            | Initial number of service replicas |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/min-scale]`     | `KNATIVE_SERVICE_DEFAULT_MIN_SCALE`     | `0`           | No       | -            | Minimum number of service replicas |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/max-scale]`     | `KNATIVE_SERVICE_DEFAULT_MAX_SCALE`     | `3`           | No       | -            | Maximum number of service replicas |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/window]`        | `KNATIVE_SERVICE_DEFAULT_WINDOW`        | `300s`        | No       | -            | Autoscaling window duration        |


#### Knative Service Container Configuration


| Property                                                                    | Environment Variable | Default Value   | Required | Applied when | Description                         |
| --------------------------------------------------------------------------- | -------------------- | --------------- | -------- | ------------ | ----------------------------------- |
| `app.knative-service-container-config.name`                                 | -                    | `app-container` | No       | -            | Container name for Knative services |
| `app.knative-service-container-config.imagePullPolicy`                      | -                    | `Always`        | No       | -            | Image pull policy                   |
| `app.knative-service-container-config.resources.requests.cpu`               | -                    | `250m`          | No       | -            | Default CPU request                 |
| `app.knative-service-container-config.resources.requests.memory`            | -                    | `250M`          | No       | -            | Default Memory request              |
| `app.knative-service-container-config.resources.requests.ephemeral-storage` | -                    | `500M`          | No       | -            | Default Ephemeral storage request   |
| `app.knative-service-container-config.resources.limits.cpu`                 | -                    | `1000m`         | No       | -            | Default CPU limit                   |
| `app.knative-service-container-config.resources.limits.memory`              | -                    | `4G`            | No       | -            | Default Memory limit                |
| `app.knative-service-container-config.resources.limits.ephemeral-storage`   | -                    | `1G`            | No       | -            | Default Ephemeral storage limit     |


#### NIM Service Configuration


| Property                                                        | Environment Variable          | Default Value    | Required | Applied when | Description                          |
| --------------------------------------------------------------- | ----------------------------- | ---------------- | -------- | ------------ | ------------------------------------ |
| `app.nim-service-config.spec.image.pullSecrets[0]`              | `NIM_SERVICE_NGC_SECRET`      | `ngc-secret`     | No       | -            | NGC (NVIDIA GPU Cloud) secret name   |
| `app.nim-service-config.spec.authSecret`                        | `NIM_SERVICE_NGC_AUTH_SECRET` | `ngc-api-secret` | No       | -            | NGC authentication secret            |
| `app.nim-service-config.spec.replicas`                          | -                             | `1`              | No       | -            | Number of NIM service replicas       |
| `app.nim-service-config.spec.storage.pvc.size`                  | -                             | `20Gi`           | No       | -            | Default Persistent volume claim size |
| `app.nim-service-config.spec.resources.limits.[nvidia.com/gpu]` | -                             | `1`              | No       | -            | Default GPU resource limit           |


### MCP Registry Configuration


| Property                             | Environment Variable             | Default Value                              | Required | Applied when | Description                                                                                                                               |
| ------------------------------------ | -------------------------------- | ------------------------------------------ | -------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `app.mcp-registry.base-url`          | `MCP_REGISTRY_BASE_URL`          | `https://registry.modelcontextprotocol.io` | No       | -            | Base URL of the MCP Registry API (used by the proxy).                                                                                     |
| `app.mcp-registry.max-pages-to-scan` | `MCP_REGISTRY_MAX_PAGES_TO_SCAN` | `25`                                       | No       | -            | Maximum number of upstream registry pages to scan per filtered request. Controls trade-off between result completeness and response time. |


### Hugging Face Configuration


| Property                                  | Environment Variable                  | Default Value                                                  | Required | Applied when | Description                                                                                                                      |
| ----------------------------------------- | ------------------------------------- | -------------------------------------------------------------- | -------- | ------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| `app.huggingface.base-url`                | `HUGGINGFACE_BASE_URL`                | `https://huggingface.co`                                       | No       | -            | Base URL for Hugging Face API                                                                                                    |
| `app.huggingface.api-token`               | `HUGGINGFACE_API_TOKEN`               | -                                                              | No       | -            | API token for authentication                                                                                                     |
| `app.huggingface.tag-cache-duration`      | `HUGGINGFACE_TAG_CACHE_DURATION`      | `24h`                                                          | No       | -            | Duration to cache tag data (e.g. 24h, 60m)                                                                                       |
| `app.huggingface.default-allowed-domains` | `HUGGINGFACE_DEFAULT_ALLOWED_DOMAINS` | `huggingface.co,transfer.xethub.hf.co,cas-server.xethub.hf.co` | No       | -            | Comma-separated list of default domains added to Cilium network policy egress for inference deployments with HuggingFace source. |


### Validation Configuration


| Property                            | Environment Variable            | Default Value                               | Required | Applied when | Description                                       |
| ----------------------------------- | ------------------------------- | ------------------------------------------- | -------- | ------------ | ------------------------------------------------- |
| `app.deployment.reserved-env-names` | `DEPLOYMENT_RESERVED_ENV_NAMES` | `PORT,K_SERVICE,K_CONFIGURATION,K_REVISION` | No       | -            | Reserved env variable names                       |
| `app.validation.resources.max-cpu-in-cores`    | `RESOURCES_MAX_CPU_IN_CORES`    | `10`                                        | No       | -            | Maximum allowed value for CPU resource (in cores) |
| `app.validation.resources.max-memory-in-mb`    | `RESOURCES_MAX_MEMORY_IN_MB`    | `100000`                                    | No       | -            | Maximum allowed value for memory resource (in mb) |
| `app.validation.resources.max-nvidia-gpu`      | `RESOURCES_MAX_NVIDIA_GPU`      | `5`                                         | No       | -            | Maximum allowed value for nvidia.com/gpu resource |
| `app.validation.resources.max-storage-size` | `RESOURCES_STORAGE_MAX_SIZE` | `200Gi`                                  | No       | -            | Maximum allowed storage size for NIM deployments. Accepts Kubernetes quantity format (e.g., '200Gi', '500Mi', '1Ti') or plain bytes |


### Export/import Configuration


| Property                      | Environment Variable      | Default Value                   | Required | Applied when | Description                                   |
| ----------------------------- | ------------------------- | ------------------------------- | -------- | ------------ | --------------------------------------------- |
| `app.config.export.file-name` | `CONFIG_EXPORT_FILE_NAME` | `dm-config.json`                | No       | -            | Name of JSON file with exported components    |
| `app.config.export.zip-name`  | `CONFIG_EXPORT_ZIP_NAME`  | `deployment-manager-config.zip` | No       | -            | Name of ZIP archive containing exported files |


### HTTP Client Configuration

Used by:

- MCP Introspection client


| Property                     | Environment Variable          | Default Value | Required | Applied when | Description                                 |
| ---------------------------- | ----------------------------- | ------------- | -------- | ------------ | ------------------------------------------- |
| `http.client.connectTimeout` | `HTTP_CLIENT_CONNECT_TIMEOUT` | `30000`       | No       | -            | HTTP client connect timeout in milliseconds |
| `http.client.readTimeout`    | `HTTP_CLIENT_READ_TIMEOUT`    | `60000`       | No       | -            | HTTP client read timeout in milliseconds    |
| `http.client.writeTimeout`   | `HTTP_CLIENT_WRITE_TIMEOUT`   | `60000`       | No       | -            | HTTP client write timeout in milliseconds   |


## Security Configuration

### General Settings


| Property                                                     | Environment Variable            | Default Value       | Required                                          | Applied when                   | Description                                                                                       |
| ------------------------------------------------------------ | ------------------------------- | ------------------- | ------------------------------------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------- |
| `config.rest.security.mode`                                  | `CONFIG_REST_SECURITY_MODE`     | `none`              | No (recommended to adjust for target environment) | -                              | Security mode (oidc, basic or none)                                                               |
| `config.rest.security.default.allowedRoles` **(deprecated)** | -                               | `ConfigAdmin,admin` | No (recommended to adjust for target environment) | config.rest.security.mode=oidc | Comma-separated list of roles with access permissions                                             |
| `config.rest.security.default.roles-mapping`                 | `SECURITY_ROLES_MAPPING`        | `{}`                | No                                                | config.rest.security.mode=oidc | JSON object with mapping of provider roles to application roles                                   |
| `config.rest.security.default.email-claim`                   | `CLAIMS_EMAIL_KEY`              | `unique_name`       | No                                                | config.rest.security.mode=oidc | Default JWT claim name (field in /userinfo response for opaque tokens) used to extract user email |
| `config.rest.security.default.principal-claim`               | `SECURITY_USER_CLAIM`           | `oid`               | No (recommended to adjust for target environment) | config.rest.security.mode=oidc | Default JWT claim name (field in /userinfo response for opaque tokens) for user identification    |
| `config.rest.security.require-email`                         | `SECURITY_REQUIRE_EMAIL`        | `false`             | No                                                | config.rest.security.mode=oidc | Controls whether an email claim is required in JWT (in /userinfo response for opaque tokens)      |
| `config.rest.security.disable-swagger-authorization`         | `DISABLE_SWAGGER_AUTHORIZATION` | `false`             | No                                                | config.rest.security.mode=oidc | Disable authorization for Swagger UI                                                              |


### Identity Providers Configuration

Applied when: config.rest.security.mode=oidc
  
The configuration is defined in environment variables
  
  
**Note:** `*` represents a wildcard placeholder, meaning **any provider name**.
  
**Example:**

- `providers.auth0.issuer`
- `providers.keycloak.client-id`


| Setting                                      | Environment Variable (as example)    | Required                             | Applied when                     | Description                                                                                                                     |
| -------------------------------------------- | ------------------------------------ | ------------------------------------ | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `providers.*.issuer`                         | `providers.azure.issuer`             | Yes                                  | `config.rest.security.mode=oidc` | List of accepted JWT token issuers for the provider                                                                             |
| `providers.*.jwk-set-uri`                    | `providers.azure.jwk-set-uri`        | Yes                                  | `config.rest.security.mode=oidc` | URI for JSON Web Key Set for the provider                                                                                       |
| `providers.*.user-info-endpoint`             | `providers.azure.user-info-endpoint` | Yes, if jwk-set-uri is not specified | `config.rest.security.mode=oidc` | URI for user info for the provider                                                                                              |
| `providers.*.aliases`                        | `providers.azure.aliases`            | Yes                                  | `config.rest.security.mode=oidc` | Aliases for accepted JWT token issuers for the provider(only for Azure provider)                                                |
| `providers.*.audiences`                      | `providers.azure.audiences`          | Yes                                  | `config.rest.security.mode=oidc` | List of accepted JWT token audiences. Specifies the intended recipients of the authorization token as defined in its aud claim. |
| `providers.*.role-claims`                    | `providers.azure.role-claims`        | No                                   | `config.rest.security.mode=oidc` | Comma-separated list of JWT claim paths used to extract user roles for the provider.                                            |
| `providers.*.allowed-roles` **(deprecated)** | `providers.azure.allowed-roles`      | No                                   | `config.rest.security.mode=oidc` | Comma-separated list of roles with access permissions for the provider                                                          |
| `providers.*.roles-mapping`                  | `providers.azure.roles-mapping`      | No                                   | `config.rest.security.mode=oidc` | JSON object with mapping of provider roles to application roles                                                                 |
| `providers.*.email-claims`                   | `providers.azure.email-claims`       | Yes, if provider is GCP              | `config.rest.security.mode=oidc` | Comma-separated list of JWT claim paths used to extract user email                                                              |
| `providers.*.principal-claim`                | `providers.azure.principal-claim`    | No                                   | `config.rest.security.mode=oidc` | Specific claim that uniquely identifies the user or service (the "principal") for whom the token was issued.                    |


**Warning**: `config.rest.security.default.allowedRoles` and `providers.*.allowed-roles` are **deprecated and will be removed in a future version**. Use `config.rest.security.default.roles-mapping` and `providers.*.roles-mapping` instead.

**Available application roles:** `FULL_ADMIN`, `READ_ONLY_ADMIN`

**Example:**

- `config.rest.security.default.roles-mapping={"ConfigAdmin":["FULL_ADMIN"],"Viewer":["READ_ONLY_ADMIN"]}`
- `providers.azure.roles-mapping={"azureAdmin":["FULL_ADMIN"],"azureViewer":["READ_ONLY_ADMIN"]}`

**Role mapping precedence:**

1. If `providers.*.roles-mapping` is specified — merged with `config.rest.security.default.roles-mapping` (provider takes precedence on overlap)
2. Else if `providers.*.allowed-roles` is specified — all those roles plus `config.rest.security.default.allowedRoles` are mapped to `FULL_ADMIN`
3. Else if `config.rest.security.default.roles-mapping` is specified — used as-is
4. Else if `config.rest.security.default.allowedRoles` is specified — all mapped to `FULL_ADMIN`
5. Else — empty mapping, all requests return 403 Forbidden

**Note:** The configuration for identity providers in the deployment manager utilizes the existing configuration from DIAL admin providers, including settings for clients and roles. 
For detailed instructions on setting up Azure and Keycloak providers, please refer to the DIAL admin providers documentation available at [DIAL Admin Providers Documentation](https://github.com/epam/ai-dial-admin-backend/tree/development/docs).

### API-Key Authentication Configuration

Applied when: `config.rest.security.mode=oidc` and `config.rest.security.api-key.enabled=true`.

When enabled, DM accepts an `Api-Key` header as an alternative credential alongside JWT/OIDC. Keys are validated by delegating to DIAL Core's `GET /v1/user/info` endpoint. DM handles both kinds of credentials Core may surface:

| Response shape from Core `/v1/user/info` | Caller                                                     | Spring principal                          | Roles mapped via                         | `UserSecurityDetails.email`         |
|------------------------------------------|------------------------------------------------------------|-------------------------------------------|------------------------------------------|-------------------------------------|
| `{roles, project}`                       | Project key (or per-request key minted from a project-key root) | `project`                                 | `api-key.roles-mapping`                  | `null`                              |
| `{roles, userClaims}`                    | Per-request key minted from a JWT root (Core → DM-MCP → DM chain) | `userClaims[default.principal-claim]`     | `default.roles-mapping`                  | `userClaims[default.email-claim]`   |

When both `Api-Key` and `Authorization` headers are present, the JWT/opaque-token path takes precedence and the `Api-Key` is ignored.

| Property                                              | Environment Variable          | Default | Required               | Description                                                                                                            |
| ----------------------------------------------------- | ----------------------------- | ------- | ---------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `config.rest.security.api-key.enabled`                | `API_KEY_ENABLED`             | `false` | No                     | If `true` (and `mode=oidc`), the `Api-Key` header is accepted as an alternative credential.                            |
| `config.rest.security.api-key.core-url`               | `API_KEY_CORE_URL`            | -       | Yes, when `enabled=true` | Base URL of DIAL Core (e.g. `http://dial-core`). DM appends `/v1/user/info` when validating API keys.                |
| `config.rest.security.api-key.cache-ttl-seconds`      | `API_KEY_CACHE_TTL_SECONDS`   | `60`    | No                     | TTL for cached introspection results (in-process Caffeine cache). Mirrors Core's own user-info cache TTL.              |
| `config.rest.security.api-key.cache-max-size`         | `API_KEY_CACHE_MAX_SIZE`      | `10000` | No                     | Maximum entries in the introspection cache. Per-request keys are higher-cardinality than project keys; raise this if eviction pressure shows up in metrics. |
| `config.rest.security.api-key.request-timeout-ms`     | `API_KEY_REQUEST_TIMEOUT_MS`  | `3000`  | No                     | Per-call timeout (connect + read) for HTTP requests to Core's `/v1/user/info`.                                         |
| `config.rest.security.api-key.roles-mapping`          | `API_KEY_ROLES_MAPPING`       | -       | At least one of `api-key.roles-mapping` or `default.roles-mapping` must be non-empty when `enabled=true` | JSON mapping of Core **project-key** role names to DM application roles. Same shape as `providers.*.roles-mapping`. Used only for the `{project, roles}` response shape. |
| `config.rest.security.api-key.startup-probe`          | `API_KEY_STARTUP_PROBE`       | `true`  | No                     | If `true`, DM probes `<core-url>/v1/user/info` once at startup and aborts boot if the URL is unreachable.              |

The JWT-rooted (`userClaims`) path reuses **OIDC defaults** to extract the principal/email and to map roles. The relevant properties are configured in the OIDC block (`config.rest.security.default.*`), not under `api-key.*`:

| Property                                            | Used by api-key path for...                  |
|-----------------------------------------------------|----------------------------------------------|
| `config.rest.security.default.roles-mapping`        | Mapping JWT-root roles to FULL_ADMIN / READ_ONLY_ADMIN |
| `config.rest.security.default.principal-claim`      | Picking the Spring principal from `userClaims`. Same value used for direct JWT auth (application default: `oid`). |
| `config.rest.security.default.email-claim`          | Picking the email from `userClaims`. Same value used for direct JWT auth (application default: `unique_name`). |

**Example — only project-key callers (CI/CD machines):**

```
API_KEY_ENABLED=true
API_KEY_CORE_URL=http://dial-core
API_KEY_ROLES_MAPPING={"admin":["FULL_ADMIN"],"default":["READ_ONLY_ADMIN"]}
```

**Example — only JWT-rooted per-request keys (admin UI through DM-MCP):**

```
API_KEY_ENABLED=true
API_KEY_CORE_URL=http://dial-core
# Reuse the same default.roles-mapping that authenticates SSO users calling DM directly:
SECURITY_ROLES_MAPPING={"sso-admin":["FULL_ADMIN"],"sso-viewer":["READ_ONLY_ADMIN"]}
# api-key.roles-mapping can be left empty in this topology.
```

**Notes:**

- API keys themselves live in DIAL Core (`config.json` for project keys, or Redis for per-request keys). DM does not store or persist keys; the cache only holds `sha256(apiKey)` for the configured TTL.
- Any non-2xx response from Core (including 401, 403, 404, 5xx) is mapped to 401 to the caller; a transport-level failure (connect timeout, refused) returns 503.
- A 200 response that contains neither `project` nor a non-empty `userClaims` is treated as malformed and rejected with 401 (not cached).
- For JWT-rooted callers, audit fields (`deployment.author`, `imageDefinition.author`, `AuditRevision.email`) record the end-user email from `userClaims[default.email-claim]`. Project-key callers have `null` email.
- Failures are never cached: revoked keys propagate after at most one cache TTL.
- When the JWT path validates a request, the api-key filter is a no-op; existing JWT flows are unaffected.
- Boot fails if **both** `api-key.roles-mapping` and `default.roles-mapping` are blank or `{}` (every authenticated caller would otherwise be rejected with 403). Either alone is sufficient.

## Cloud Provider Configuration

### Azure Configuration


| Setting                 | Environment Variable | Default | Required | Applied when                        | Description                                                  |
| ----------------------- | -------------------- | ------- | -------- | ----------------------------------- | ------------------------------------------------------------ |
| azure.auth.type         | AUTH_AZURE_TYPE      | none    | Yes      | -                                   | Azure authentication method (values: credential,cli,managed) |
| azure.auth.clientId     | AZURE_CLIENT_ID      | -       | Yes      | azure.auth.type=credential          | Azure service principal client ID                            |
| azure.auth.tenantId     | AZURE_TENANT_ID      | -       | Yes      | azure.auth.type in [cli,credential] | Azure tenant ID                                              |
| azure.auth.clientSecret | AZURE_CLIENT_SECRET  | -       | Yes      | azure.auth.type=credential          | Azure service principal client secret                        |


## Datasource Configuration


| Setting                        | Environment Variable              | Default                                                                                                              | Required                                          | Applied when                                                   | Description                                                         |
| ------------------------------ | --------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------- |
| datasource.vendor              | DATASOURCE_VENDOR                 | H2                                                                                                                   | No (recommended to adjust for target environment) | -                                                              | Datasource vendor: - H2 - POSTGRES - MS_SQL_SERVER                  |
| datasource.auth.type           | DATASOURCE_AUTH_TYPE              | basic                                                                                                                | No                                                | -                                                              | Datasource auth type: - basic (username and password) - azure - gcp |
| h2.datasource.url              | H2_DATASOURCE_URL                 | jdbc:h2:file:${H2_FILE};${H2_OPS}                                                                                    | No                                                | datasource.vendor=H2                                           | JDBC URL for H2 database connection                                 |
|                                | H2_FILE                           | ./data/testdb                                                                                                        | No (recommended to adjust for target environment) | datasource.vendor=H2                                           | H2 database file                                                    |
|                                | H2_OPS                            | CIPHER=AES;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE                                                                  | No                                                | datasource.vendor=H2                                           | H2 database connection options                                      |
| h2.datasource.masterKey        | H2_DATASOURCE_MASTERKEY           | -                                                                                                                    | Yes                                               | datasource.vendor=H2                                           | Master key for H2 database encryption                               |
| h2.datasource.encryptedFileKey | H2_DATASOURCE_ENCRYPTEDFILEKEY    | -                                                                                                                    | Yes                                               | datasource.vendor=H2                                           | Encrypted file key for H2 database                                  |
| h2.datasource.password         | H2_DATASOURCE_PASSWORD            | -                                                                                                                    | Yes                                               | datasource.vendor=H2                                           | Password for H2 database access                                     |
| postgres.datasource.url        | POSTGRES_DATASOURCE_URL           | jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DATABASE}?${POSTGRES_OPS:}                            | No                                                | datasource.vendor=POSTGRES                                     | JDBC URL for Postgres database connection                           |
|                                | POSTGRES_HOST                     | localhost                                                                                                            | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES                                     | Postgres database host                                              |
|                                | POSTGRES_PORT                     | 5432                                                                                                                 | No                                                | datasource.vendor=POSTGRES                                     | Postgres database port                                              |
|                                | POSTGRES_DATABASE                 | testdb                                                                                                               | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES                                     | Postgres database name                                              |
|                                | POSTGRES_OPS                      | -                                                                                                                    | No                                                | datasource.vendor=POSTGRES                                     | Postgres database connection options                                |
| postgres.datasource.username   | POSTGRES_DATASOURCE_USERNAME      | postgres                                                                                                             | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES and datasource.auth.type=basic      | Username for Postgres database access                               |
| postgres.datasource.password   | POSTGRES_DATASOURCE_PASSWORD      | postgres                                                                                                             | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES and datasource.auth.type=basic      | Password for Postgres database access                               |
| sqlserver.datasource.url       | SQLSERVER_DATASOURCE_URL          | jdbc:sqlserver://${MS_SQL_SERVER_HOST}:${MS_SQL_SERVER_PORT};database=${MS_SQL_SERVER_DATABASE};${MS_SQL_SERVER_OPS} | No                                                | datasource.vendor=MS_SQL_SERVER                                | JDBC URL for MSSQL Server database connection                       |
|                                | MS_SQL_SERVER_HOST                | localhost                                                                                                            | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database host                                          |
|                                | MS_SQL_SERVER_PORT                | 1433                                                                                                                 | No                                                | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database port                                          |
|                                | MS_SQL_SERVER_DATABASE            | testdb                                                                                                               | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database name                                          |
|                                | MS_SQL_SERVER_OPS                 | encrypt=false;                                                                                                       | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database connection options                            |
| sqlserver.datasource.username  | MS_SQL_SERVER_DATASOURCE_USERNAME | sa                                                                                                                   | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER and datasource.auth.type=basic | Username for MSSQL Server database access                           |
| sqlserver.datasource.password  | MS_SQL_SERVER_DATASOURCE_PASSWORD | SQLServerPassword1                                                                                                   | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER and datasource.auth.type=basic | Password for MSSQL Server database access                           |
| spring.jpa.hibernate.ddl-auto  | SPRING_JPA_HIBERNATE_DDL_AUTO     | validate                                                                                                             | No                                                | -                                                              | Hibernate schema generation strategy                                |
| spring.flyway.enabled          | SPRING_FLYWAY_ENABLED             | true                                                                                                                 | No                                                | -                                                              | Enable or disable Flyway database migrations                        |


When using MS_SQL_SERVER we recommend to set case-sensitive, accept-sensitive database collation, e.g. SQL_Latin1_General_CP1_CS_AS. See [Collation and Unicode support](https://learn.microsoft.com/en-us/sql/relational-databases/collations/collation-and-unicode-support).

ai-dial-admin-backend/secrets-utils/generate_h2_secrets.sh can help to generate H2_DATASOURCE_MASTERKEY/H2_DATASOURCE_ENCRYPTEDFILEKEY/H2_DATASOURCE_PASSWORD if H2 db is used.

### JVM and Runtime Configuration


| Property | Environment Variable | Default Value | Required | Applied when | Description                                                                                   |
| -------- | -------------------- | ------------- | -------- | ------------ | --------------------------------------------------------------------------------------------- |
| -        | `DEBUG_OPTS`         | `""`          | No       | -            | JVM debug and monitoring options passed to java command (e.g., for debugging, JMX, profiling) |


## Actuator Configuration


| Setting                                   | Environment Variable                      | Default           | Required | Applied when | Description                  |
| ----------------------------------------- | ----------------------------------------- | ----------------- | -------- | ------------ | ---------------------------- |
| management.endpoints.web.exposure.include | MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE | prometheus,health | No       | -            | Actuator endpoints to expose |
| management.endpoint.health.show-details   | MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS   | always            | No       | -            | Show health information      |
| management.server.port                    | MANAGEMENT_SERVER_PORT                    | 9464              | No       | -            | Actuator endpoints port      |


## Logging Configuration

### Log Levels


| Property                                                             | Environment Variable         | Default Value | Required | Applied when | Description                                   |
| -------------------------------------------------------------------- | ---------------------------- | ------------- | -------- | ------------ | --------------------------------------------- |
| `spring.logging.level.org.springframework.security`                  | `SPRING_SECURITY_LOG_LEVEL`  | `INFO`        | No       | -            | Log level for Spring Security                 |
| `spring.logging.level.com.epam.aidial.deployment.manager`            | `APP_LOG_LEVEL`              | `INFO`        | No       | -            | Log level for application code                |
| `spring.logging.level.org.hibernate.SQL`                             | `HIBERNATE_SQL_LOG_LEVEL`    | `INFO`        | No       | -            | Log level for Hibernate SQL statements        |
| `spring.logging.level.org.hibernate.type.descriptor.sql.BasicBinder` | `HIBERNATE_BINDER_LOG_LEVEL` | `INFO`        | No       | -            | Log level for Hibernate SQL parameter binding |


### Tomcat Access Logs


| Property                          | Environment Variable       | Default Value                                                                                                                               | Required | Applied when | Description                    |
| --------------------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------ | ------------------------------ |
| `server.tomcat.accesslog.pattern` | -                          | `"request: method=%m uri=\"%U\" response: statuscode=%s bytes=%b duration=%D(ms) client: remoteip=%a user=%u useragent=\"%{User-Agent}i\""` | No       | -            | Pattern for Tomcat access logs |
| `server.tomcat.accesslog.enabled` | `TOMCAT_ACCESSLOG_ENABLED` | `false`                                                                                                                                     | No       | -            | Enable Tomcat access logs      |


### Dynamic Logger Configuration


| Property                        | Environment Variable  | Default Value                    | Required | Applied when | Description                                                      |
| ------------------------------- | --------------------- | -------------------------------- | -------- | ------------ | ---------------------------------------------------------------- |
| `logger.configuration.path`     | `LOG_CONFIG_PATH`     | `log-config/logging.levels.json` | No       | -            | Path to logging configuration file                               |
| `logger.configuration.interval` | `LOG_CONFIG_INTERVAL` | `10`                             | No       | -            | Interval (in seconds) to check for logging configuration changes |


### Method Execution Logging


| Property                                               | Environment Variable                               | Default Value                                                                      | Required | Applied when                                                                   | Description                           |
| ------------------------------------------------------ | -------------------------------------------------- | ---------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------ | ------------------------------------- |
| `app.customizable-trace-interceptor.enabled`           | `CUSTOMIZABLE_TRACE_INTERCEPTOR_ENABLED`           | `true`                                                                             | No       | -                                                                              | Enable method execution tracing       |
| `app.customizable-trace-interceptor.messages.ENTER`    | `CUSTOMIZABLE_TRACE_INTERCEPTOR_ENTER_MESSAGE`     | `'Enter: $[methodName](): $[arguments]'`                                           | No       | -                                                                              | Message template for method entry     |
| `app.customizable-trace-interceptor.messages.EXIT`     | `CUSTOMIZABLE_INTERCEPTOR_EXIT_MESSAGE`            | `'Exit: $[methodName]() : in $[invocationTime] ms, returnValue: $[returnValue]'`   | No       | -                                                                              | Message template for method exit      |
| `appcustomizable-trace-interceptor.messages.EXCEPTION` | `CUSTOMIZABLE_TRACE_INTERCEPTOR_EXCEPTION_MESSAGE` | `'Exception: $[methodName]() : in $[invocationTime] ms'`                           | No       | -                                                                              | Message template for method exception |
| `app.trace-log-advisor.expression`                     | -                                                  | @annotation(logging.configuration.com.epam.aidial.deployment.manager.LogExecution) |          | @within(logging.configuration.com.epam.aidial.deployment.manager.LogExecution) | No                                    |


## OpenTelemetry Configuration


| Setting                     | Environment Variable        | Default                         | Required | Applied when            | Description                                       |
| --------------------------- | --------------------------- | ------------------------------- | -------- | ----------------------- | ------------------------------------------------- |
| otel.sdk.disabled           | OTEL_SDK_DISABLED           | true                            | No       | -                       | Disable OpenTelemetry SDK                         |
| otel.service.name           | OTEL_SERVICE_NAME           | dial-deployment-manager-backend | No       | -                       | Service name                                      |
| otel.exporter.otlp.endpoint | OTEL_EXPORTER_OTLP_ENDPOINT |                                 | Yes      | otel.sdk.disabled=false | OpenTelemetry collector endpoint                  |
| otel.exporter.otlp.protocol | OTEL_EXPORTER_OTLP_PROTOCOL |                                 | Yes      | otel.sdk.disabled=false | Protocol for OpenTelemetry data export            |
| otel.logs.exporter          | OTEL_LOGS_EXPORTER          | otlp                            | No       | -                       | Exporter for application logs                     |
| otel.traces.exporter        | OTEL_TRACES_EXPORTER        | otlp                            | No       | -                       | Exporter for distributed traces                   |
| otel.metrics.exporter       | OTEL_METRICS_EXPORTER       | otlp                            | No       | -                       | Exporter for application metrics                  |
| otel.resource.attributes    | OTEL_RESOURCE_ATTRIBUTES    |                                 | No       | -                       | Key-value pairs to be used as resource attributes |


### Distributed Tracing

The application uses OpenTelemetry for distributed tracing and automatically includes trace IDs in HTTP response headers and error responses. This enables request correlation across services and makes debugging easier using tools like Kibana or browser developer tools.

#### Response Headers

All HTTP responses include the following header (when OpenTelemetry trace context is available):

- `**traceparent*`*: W3C Trace Context standard header for interoperability. Format: `00-{trace-id}-{span-id}-{trace-flags}` (e.g., `00-5bf82f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`)

#### Response Body Structure

**Success Responses:**

Success responses are returned as-is without wrapping. Trace IDs are available in `traceparent` response headers only.

**Error Responses:**

Error responses include trace information directly in the error object:

```json
{
  "path": "/api/v1/deployments",
  "method": "GET",
  "status": 404,
  "error": "Not Found",
  "message": "Deployment not found",
  "traceparent": "00-5bf82f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
}
```

#### Client Integration - Passing Trace Context

To maintain OpenTelemetry trace context when making requests to this service, clients should:

**Use `traceparent` header**: Send the W3C Trace Context header to propagate trace context:

```
traceparent: 00-{trace-id}-{span-id}-{trace-flags}
```

Example: `traceparent: 00-5bf82f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`

The service will automatically extract and propagate the trace context. If no trace context is provided, the service will generate a new trace.

**Frontend/Client Examples:**

- **JavaScript/TypeScript**: Use OpenTelemetry JavaScript SDK to automatically inject `traceparent` header
- **Manual**: Extract trace ID from previous response headers and include in subsequent requests
- **Browser Developer Tools**: Inspect Network tab to see trace IDs in response headers

#### Trace ID Generation

- Trace IDs are extracted exclusively from OpenTelemetry span context
- If OpenTelemetry is disabled (`otel.sdk.disabled=true`) or trace context is unavailable, headers may not be set
- The service relies solely on OpenTelemetry for trace ID generation - no custom correlation ID logic

## Required Kubernetes RBAC

The application needs scoped permissions in four namespaces:

- Build namespace: where Jobs run to build, analyze, and copy images
- Knative namespace: where `Knative` Services are created/managed
- NIM namespace: where NVIDIA `NIMService` resources are created/managed
- KServe namespace: where `InferenceService` resources are created/managed

Map these namespaces via configuration:

- Build namespace → `app.build-namespace` (`K8S_BUILD_NAMESPACE`)
- Knative namespace → `app.knative.deploy.namespace` (`K8S_KNATIVE_DEPLOYMENT_NAMESPACE`)
- NIM namespace → `app.nim.deploy.namespace` (`K8S_NIM_DEPLOYMENT_NAMESPACE`)
- KServe namespace → `app.kserve.deploy.namespace` (`K8S_KSERVE_DEPLOYMENT_NAMESPACE`)

Example Roles (replace placeholders with your namespaces):

```yaml
# Build namespace Role (used for Jobs: build, copy, analyze)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ai-dial-admin-mcp-role
  namespace: <build namespace>
rules:
  # Jobs for building/copying images
  - apiGroups: ["batch"]
    resources: ["jobs"]
    verbs: ["get","list","watch","create","delete"]
  # Read job pod logs
  - apiGroups: [""]
    resources: ["pods","pods/log"]
    verbs: ["get","list","watch"]
  # Secrets/ConfigMaps for jobs
  - apiGroups: [""]
    resources: ["secrets","configmaps"]
    verbs: ["get","create","delete"]
---
# Deploy namespace Role (Knative Services)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ai-dial-admin-deployment-manager-role
  namespace: <deploy namespace>
rules:
  # Knative Services
  - apiGroups: ["serving.knative.dev"]
    resources: ["services"]
    verbs: ["get","list","watch","create","update","delete"]
  # Pod logs and optional pod delete
  - apiGroups: [""]
    resources: ["pods","pods/log","events"]
    verbs: ["get","list","watch","delete"]
  # Secrets for Knative Services
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get","create","delete"]
---
# NIM namespace Role (NVIDIA NIMService CRD)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ai-dial-admin-deployment-manager-role
  namespace: <nim namespace>
rules:
  # NIM Services
  - apiGroups: ["apps.nvidia.com"]
    resources: ["nimservices"]
    verbs: ["get","list","watch","create","delete"]
  # Read NIM workload pod logs
  - apiGroups: [""]
    resources: ["pods","pods/log","events"]
    verbs: ["get","list","watch"]
  # Secrets for NIM services
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get","create","delete"]
---
# KServe namespace Role (InferenceService CRD)
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: ai-dial-admin-deployment-manager-role
  namespace: <kserve namespace>
rules:
  # KServe InferenceServices lifecycle (create/update/delete/watch)
  - apiGroups: ["serving.kserve.io"]
    resources: ["inferenceservices"]
    verbs: ["get","list","watch","create","delete"]
  # Pods and logs for deployed inference services
  - apiGroups: [""]
    resources: ["pods","pods/log","events"]
    verbs: ["get","list","watch"]
  # Access to referenced Secrets (if env vars point to K8s secrets)
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["get"]
```

Note: Create corresponding RoleBindings to bind each Role to the ServiceAccount used by this application in each namespace.

## Notes

1. **Security**: Always use secure values for sensitive properties like database passwords, registry credentials, and JWT secrets in production environments.
2. **Kubernetes Configuration**: The application supports two modes of Kubernetes authentication:
  - `CONFIG_FILE`: Uses kubeconfig file (default for development)
  - `TOKEN`: Uses OAuth token (recommended for production)
3. **Database Encryption**: The H2 database uses AES encryption. The master key and encrypted file key must be provided for database access. [https://github.com/epam/ai-dial-admin-backend/blob/development/secrets-utils/generate_h2_secrets.sh](https://github.com/epam/ai-dial-admin-backend/blob/development/secrets-utils/generate_h2_secrets.sh) can help to generate H2_DATASOURCE_MASTERKEY/H2_DATASOURCE_ENCRYPTEDFILEKEY/H2_DATASOURCE_PASSWORD if H2 db is used.
4. **Internal API Endpoint**: The /api/internal/v1/deployments endpoint is intentionally left open without security to allow the admin service to access it from within the cluster without service-to-service authentication. This may require modifications to your service mesh or network policies to allow inbound traffic to this path.

