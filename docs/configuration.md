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
| Property | Environment Variable | Default Value        | Required | Applied when | Description |
|----------|---------------------|----------------------|----------|--------------|-------------|
| `spring.application.name` | - | `deployment-manager` | No | - | Application name |

#### Database Configuration (JPA/Hibernate)
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `spring.jpa.database-platform` | - | `org.hibernate.dialect.H2Dialect` | No | - | JPA database platform |
| `spring.jpa.hibernate.ddl-auto` | - | `validate` | No | - | Hibernate DDL auto mode |
| `spring.jpa.show-sql` | - | `false` | No | - | Enable SQL logging |
| `spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access` | - | `true` | No | - | Allow JDBC metadata access |

#### Database Migration (Flyway)
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `spring.flyway.enabled` | - | `true` | No | - | Enable Flyway migrations |
| `spring.flyway.default-schema` | - | `PUBLIC` | No | - | Default schema for migrations |
| `spring.flyway.locations` | `DB_VENDOR` | `classpath:db/migration/H2/` | No | - | Migration scripts location |
| `spring.flyway.baseline-on-migrate` | - | `true` | No | - | Enable baseline on migrate |
| `spring.flyway.baseline-version` | - | `1.1` | No | - | Baseline version |

#### MVC Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `spring.mvc.async.request-timeout` | - | `600000` | No | - | Async request timeout in milliseconds (10 minutes) |

### Application-Specific Configuration

#### Kubernetes Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `app.kubernetes.connect-type` | `K8S_CONNECT_TYPE` | `CONFIG_FILE` | No | - | Kubernetes connection type (CONFIG_FILE or TOKEN) |
| `app.kubernetes.config_file.kube-config` | - | `${user.home}/.kube/config` | No | `connect-type=CONFIG_FILE` | Path to kubeconfig file |
| `app.kubernetes.config_file.contexts.deploy-context` | `K8S_DEPLOY_CONTEXT` | - | No | `connect-type=CONFIG_FILE` | Kubernetes deployment context |
| `app.kubernetes.token.master-url` | `K8S_MASTER_URL` | - | Yes | `connect-type=TOKEN` | Kubernetes master URL for token authentication |
| `app.kubernetes.token.oauth-token` | `K8S_OAUTH_TOKEN` | - | Yes | `connect-type=TOKEN` | OAuth token for Kubernetes authentication |

#### Docker Registry Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description                                                                                                                                                     |
|----------|---------------------|---------------|----------|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app.registry.url` | `DOCKER_REGISTRY` | `test-docker-registry` | No (recommended to adjust for your environment) | - | Docker registry URL. This is the registry where the service will publish built Docker images.                                                                   |
| `app.registry.protocol` | `DOCKER_REGISTRY_PROTOCOL` | `https` | No | - | Docker registry protocol                                                                                                                                        |
| `app.registry.auth` | `DOCKER_REGISTRY_AUTH` | `NONE` | No (recommended to adjust for target environment) | - | Docker registry authentication scheme (values: NONE, BASIC)                                                                                                     |
| `app.registry.user` | `DOCKER_REGISTRY_USER` | - | No | `auth=BASIC` | Docker registry username                                                                                                                                        |
| `app.registry.password` | `DOCKER_REGISTRY_PASSWORD` | - | No | `auth=BASIC` | Docker registry password                                                                                                                                        |
| `app.registry.trusted-private-registries` | `TRUSTED_PRIVATE_REGISTRIES` | - | No | - | JSON array of registry configuration objects. Each object must have at least `"registry"` (the registry host), and may include `"authScheme"` (`"NONE"` or `"BASIC"`), `"protocol"` (`"https"` by default), `"user"`, and `"password"` (for `"BASIC"` auth). Example:<br><br>```[{"registry":"my.private.registry","authScheme":"BASIC","user":"user1","password":"pass1"},{"registry":"another.registry","protocol":"http","authScheme":"NONE"}]```<br><br>These are read-only registries from which the service is allowed to copy images into the `DOCKER_REGISTRY`. |

#### Git Configuration
| Property                        | Environment Variable        | Default Value | Required | Applied when | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|---------------------------------|-----------------------------|---------------|----------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `app.git.trusted-private-repos` | `TRUSTED_PRIVATE_GIT_REPOS` | -             | No       | -            | JSON array of Git repository configuration objects. Each object must have at least `"host"` (the Git repository host), and may include `"protocol"` (`"https"` or `"http"`, default: `"https"`), `"user"`, `"password"`, `"token"`, `"sshKeyPath"`, and `"sshKnownHostsPath"`. The `"protocol"` field is only applicable for HTTPS/HTTP authentication (when using `"user"`/`"password"` or `"token"`), and is not applicable when using SSH key authentication (`"sshKeyPath"`). For SSH authentication, `"sshKeyPath"` and `"sshKnownHostsPath"` must be file paths (not file contents). The files are read at application startup and the application will fail to start if the files are not found. Example:<br><br>```[{"host":"git.example.com","protocol":"https","user":"user1","password":"pass1"},{"host":"git.private.com","sshKeyPath":"/path/to/id_rsa","sshKnownHostsPath":"/path/to/known_hosts"}]```<br><br>These are trusted private Git repositories from which the service is allowed to clone code during build operations. |

#### LogReader Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `app.pipeline.log-reader.max-log-count` | `PIPELINE_RUNNER_MAX_LOG_COUNT` | `10000` | No | - | Maximum number of log entries to read |
| `app.pipeline.log-reader.max-log-length` | `PIPELINE_RUNNER_MAX_LOG_LENGTH` | `10000` | No | - | Maximum length of log entries |

#### Notification (SSE/Logs/Statuses) Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description                                  |
|----------|---------------------|--------------|----------|--------------|----------------------------------------------|
| `app.sse.heartbeat.interval` | - | `10000` | No | - | SSE heartbeat interval in milliseconds       |
| `app.sse.poll-interval-ms` | - | `1000` | No | - | SSE streaming cycle interval in milliseconds |

#### Build and Deployment Configuration

| Property                 | Environment Variable  | Default Value                           | Required                                             | Applied when | Description                                                                                           |
|--------------------------|-----------------------|-----------------------------------------|------------------------------------------------------|--------------|-------------------------------------------------------------------------------------------------------|
| `app.build-namespace`    | `K8S_BUILD_NAMESPACE` | `default`                               | No (recommended to adjust for target environment)    | -            | Kubernetes namespace for build operations                                                             |
| `app.git-clone-image`    | `GIT_CLONE_IMAGE`     | `alpine/git:latest`                     | No (recommended to use a specific tag in production) | -            | Docker image for Git cloning in init containers. Must include git and openssh-client for SSH support. |
| `app.builder-image`      | -                     | `gcr.io/kaniko-project/executor:latest` | No (recommended to use a specific tag in production) | -            | Docker image for building containers                                                                  |
| `app.analyser-image`     | -                     | `anchore/syft:latest`                   | No (recommended to use a specific tag in production) | -            | Docker image used for analyzing container images                                                      |
| `app.copy-image`         | -                     | `quay.io/skopeo/stable:latest`          | No (recommended to use a specific tag in production) | -            | Docker image for copying images                                                                       |
| `app.docker-config-path` | -                     | `/kaniko/.docker/config.json`           | No                                                   | -            | Path to the location where the Docker config file is mounted for build containers.                    |
| `app.cilium-network-policies-enabled` | `CILIUM_NETWORK_POLICIES_ENABLED`                     | `false`                                 | No                                                   | -            | Flag that allows to enable Cilium network policies for image build and deployments.                   |
| `app.image-name-format`    | `IMAGE_NAME_FORMAT`      | `app-%s`    | No    | -            | Name format for images that are built using Deployment Manager. Must contain %s that will be replaced by image definition ID.                                                                              |
| `app.resource-name-prefix` | `RESOURCE_NAME_PREFIX`   | -          | No    | -            | Prefix that will be added to all resources that image build and deployments produce. Important note: do not change this value on exising setups, otherwise existing images and K8s resources will be lost. |

#### MCP Proxy Configuration

| Property                            | Environment Variable                | Default Value | Required | Applied when | Description                                                                                                                              |
|-------------------------------------|-------------------------------------|---------------|----------|--------------|------------------------------------------------------------------------------------------------------------------------------------------|
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
|-----------------------------------------------|-------------------------------------------------------|---------------|---------------------------------------------------|--------------|------------------------------------------------------------------------|
| `app.knative.enabled`                         | `K8S_KNATIVE_ENABLED`                                 | `true`        | No                                                | -            | Enable or disable Knative deployment support                           |
| `app.knative.deploy.namespace`                | `K8S_KNATIVE_DEPLOYMENT_NAMESPACE`                    | `default`     | No (recommended to adjust for target environment) | -            | Kubernetes namespace for Knative deployments                           |
| `app.knative.deploy.startup-timeout`          | `K8S_KNATIVE_DEPLOYMENT_STARTUP_TIMEOUT_SEC`          | `300`         | No                                                | -            | Knative service startup timeout in seconds                             |
| `app.knative.deploy.undeploy-timeout`         | `K8S_KNATIVE_DEPLOYMENT_UNDEPLOY_TIMEOUT_SEC`         | `300`         | No                                                | -            | Knative service undeploy timeout in seconds                            |
| `app.knative.deploy.informer-resync-interval` | `K8S_KNATIVE_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` | `60`          | No                                                | -            | Kubernetes informer resync interval in seconds for Knative deployments |

#### NIM (NVIDIA Inference Microservices) Configuration
| Property                                  | Environment Variable                              | Default Value | Required                                          | Applied when | Description                                                        |
|-------------------------------------------|---------------------------------------------------|---------------|---------------------------------------------------|--------------|--------------------------------------------------------------------|
| `app.nim.enabled`                         | `K8S_NIM_ENABLED`                                 | `true`        | No                                                | -            | Enable or disable NIM deployment support                           |
| `app.nim.deploy.namespace`                | `K8S_NIM_DEPLOYMENT_NAMESPACE`                    | `default`     | No (recommended to adjust for target environment) | -            | Kubernetes namespace for NIM deployments                           |
| `app.nim.deploy.startup-timeout`          | `K8S_NIM_DEPLOYMENT_STARTUP_TIMEOUT_SEC`          | `3600`        | No                                                | -            | NIM service startup timeout in seconds (1 hour)                    |
| `app.nim.deploy.informer-resync-interval` | `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` | `60`          | No                                                | -            | Kubernetes informer resync interval in seconds for NIM deployments |
| `app.nim.deploy.use-cluster-internal-url` | `K8S_NIM_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL`     | `true`        | No (recommended to adjust for target environment) | -            | Whether to use cluster-internal URL for NIM services.              |

#### KServe Configuration
| Property                                     | Environment Variable                              | Default Value | Required                                          | Applied when | Description                                                                                                     |
|----------------------------------------------|---------------------------------------------------|---------------|---------------------------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------|
| `app.kserve.enabled`                         | `K8S_KSERVE_ENABLED`                              | `true`        | No                                                | -            | Enable or disable KServe deployment support                                                                     |
| `app.kserve.deploy.namespace`                | `K8S_KSERVE_DEPLOYMENT_NAMESPACE`                 | `default`     | No (recommended to adjust for target environment) | -            | Kubernetes namespace for KServe deployments (max 14 chars due to https://github.com/kserve/kserve/issues/4807). |
| `app.kserve.deploy.startup-timeout`          | `K8S_KSERVE_DEPLOYMENT_STARTUP_TIMEOUT_SEC`       | `3600`        | No                                                | -            | Seconds to wait for a KServe service to become ready.                                                           |
| `app.kserve.deploy.informer-resync-interval` | `K8S_NIM_DEPLOYMENT_INFORMER_RESYNC_INTERVAL_SEC` | `60`          | No                                                | -            | Kubernetes informer resync interval in seconds for KServe deployments                                           |
| `app.kserve.deploy.use-cluster-internal-url` | `K8S_KSERVE_DEPLOYMENT_USE_CLUSTER_INTERNAL_URL`  | `true`        | No (recommended to adjust for target environment) | -            | Whether to use cluster-internal URL for KServe services.                                                        |

#### Cleanup and Maintenance Configuration

| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `app.resource-cleaner-cron` | `RESOURCE_CLEANER_CRON` | `0 0 5 * * *` | No | - | Cron expression for resource cleanup (daily at 5 AM) |
| `app.resource-cleaner-take-size` | `RESOURCE_CLEANER_TAKE_SIZE` | `100` | No | - | Batch size for resource cleanup operations |
| `app.resource-cleaner-scheduler-lock-at-most-for` | `RESOURCE_CLEANER_SCHEDULER_LOCK_AT_MOST_FOR` | `10m` | No | - | Maximum lock duration for resource cleaner |
| `app.component-cleaner-cron` | `COMPONENT_CLEANER_CRON` | `0 0 5 * * *` | No | - | Cron expression for component cleanup (daily at 5 AM) |
| `app.component-cleaner-scheduler-lock-at-most-for` | `COMPONENT_CLEANER_SCHEDULER_LOCK_AT_MOST_FOR` | `10m` | No | - | Maximum lock duration for component cleaner |

#### Deployment State Synchronization Configuration
| Property | Environment Variable | Default Value    | Required | Applied when | Description                                                                        |
|----------|---------------------|------------------|----------|--------------|------------------------------------------------------------------------------------|
| `app.deployment-reconcile-cron` | `DEPLOYMENT_RECONCILE_CRON` | `0 0 */2 * * *`  | No | - | Cron expression for deployment state reconciliation                                |
| `app.deployment-reconcile-scheduler-lock-at-most-for` | `DEPLOYMENT_RECONCILE_SCHEDULER_LOCK_AT_MOST_FOR` | `30m`            | No | - | Maximum lock duration for deployment reconciliation |
| `app.deployment-pending-check-cron` | `DEPLOYMENT_PENDING_CHECK_CRON` | `0 */15 * * * *` | No | - | Cron expression for checking pending deployments |
| `app.deployment-pending-check-scheduler-lock-at-most-for` | `DEPLOYMENT_PENDING_CHECK_SCHEDULER_LOCK_AT_MOST_FOR` | `5m`             | No | - | Maximum lock duration for pending deployment checks |
| `app.deployment-reconcile-pending-cut-off-mins` | `DEPLOYMENT_RECONCILE_PENDING_CUT_OFF_MINS` | `10` | No | - | Maximum time allowed for deployment to stay in pending state until marking as crashed |
| `app.deployment-healthcheck-enabled`            | `DEPLOYMENT_HEALTHCHECK_ENABLED`            | `false`  | No | - | Enable or disable deployment healthchecks that run on deploy & state reconciliation |

#### Deployment Watcher Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description                                                                           |
|----------|---------------------|---------------|----------|--------------|---------------------------------------------------------------------------------------|
| `app.deployment-bootstrap-enabled` | `DEPLOYMENT_BOOTSTRAP_ENABLED` | `true` | No | - | Enable or disable deployment state synchronization process on app startup (bootstrap) |
| `app.deployment-bootstrap-batch-size` | `DEPLOYMENT_BOOTSTRAP_BATCH_SIZE` | `50` | No | - | Number of deployments to process in a single batch during bootstrap                   |
| `app.deployment-bootstrap-threads` | `DEPLOYMENT_BOOTSTRAP_THREADS` | `2` | No | - | Number of threads to use for deployment bootstrap processing                          |
| `app.watcher-failure-reset-interval-ms` | `WATCHER_FAILURE_RESET_INTERVAL_MS` | `600000` | No | - | Interval in milliseconds after which to reset watcher failure status                  |

#### Knative Service Default Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/initial-scale]` | `KNATIVE_SERVICE_DEFAULT_INITIAL_SCALE` | `1` | No | - | Initial number of service replicas |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/min-scale]` | `KNATIVE_SERVICE_DEFAULT_MIN_SCALE` | `0` | No | - | Minimum number of service replicas |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/max-scale]` | `KNATIVE_SERVICE_DEFAULT_MAX_SCALE` | `3` | No | - | Maximum number of service replicas |
| `app.knative-service-config.spec.template.metadata.annotations.[autoscaling.knative.dev/window]` | `KNATIVE_SERVICE_DEFAULT_WINDOW` | `300s` | No | - | Autoscaling window duration |

#### Knative Service Container Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `app.knative-service-container-config.name` | - | `app-container` | No | - | Container name for Knative services |
| `app.knative-service-container-config.imagePullPolicy` | - | `Always` | No | - | Image pull policy |
| `app.knative-service-container-config.resources.requests.cpu` | - | `250m` | No | - | Default CPU request |
| `app.knative-service-container-config.resources.requests.memory` | - | `250M` | No | - | Default Memory request |
| `app.knative-service-container-config.resources.requests.ephemeral-storage` | - | `500M` | No | - | Default Ephemeral storage request |
| `app.knative-service-container-config.resources.limits.cpu` | - | `1000m` | No | - | Default CPU limit |
| `app.knative-service-container-config.resources.limits.memory` | - | `4G` | No | - | Default Memory limit |
| `app.knative-service-container-config.resources.limits.ephemeral-storage` | - | `1G` | No | - | Default Ephemeral storage limit |

#### NIM Service Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `app.nim-service-config.spec.image.pullSecrets[0]` | `NIM_SERVICE_NGC_SECRET` | `ngc-secret` | No | - | NGC (NVIDIA GPU Cloud) secret name |
| `app.nim-service-config.spec.authSecret` | `NIM_SERVICE_NGC_AUTH_SECRET` | `ngc-api-secret` | No | - | NGC authentication secret |
| `app.nim-service-config.spec.replicas` | - | `1` | No | - | Number of NIM service replicas |
| `app.nim-service-config.spec.storage.pvc.size` | - | `20Gi` | No | - | Default Persistent volume claim size |
| `app.nim-service-config.spec.resources.limits.[nvidia.com/gpu]` | - | `1` | No | - | Default GPU resource limit |

### Validation Configuration

| Property                            | Environment Variable                  | Default Value                                                  | Required | Applied when  | Description                 |
|-------------------------------------|---------------------------------------|----------------------------------------------------------------|----------|---------------|-----------------------------|
| `app.deployment-reserved-env-names` | `DEPLOYMENT_RESERVED_ENV_NAMES`       | `PORT,K_SERVICE,K_CONFIGURATION,K_REVISION`                    | No       | -             | Reserved env variable names |

### HTTP Client Configuration

Used by:
* MCP Introspection client

| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `http.client.connectTimeout` | `HTTP_CLIENT_CONNECT_TIMEOUT` | `30000` | No | - | HTTP client connect timeout in milliseconds |
| `http.client.readTimeout` | `HTTP_CLIENT_READ_TIMEOUT` | `60000` | No | - | HTTP client read timeout in milliseconds |
| `http.client.writeTimeout` | `HTTP_CLIENT_WRITE_TIMEOUT` | `60000` | No | - | HTTP client write timeout in milliseconds |

## Security Configuration

### General Settings
| Property                                             | Environment Variable                  | Default Value                                                  | Required                                          | Applied when                      | Description                                           |
|------------------------------------------------------|---------------------------------------|----------------------------------------------------------------|---------------------------------------------------|-----------------------------------|-------------------------------------------------------|
| `config.rest.security.mode`                          | `CONFIG_REST_SECURITY_MODE`           | `none`                                                         | No (recommended to adjust for target environment) | -                                 | Security mode (oidc, basic or none)                   |
| `config.rest.security.default.allowedRoles`          | -                                     | `ConfigAdmin,admin`                                            | No (recommended to adjust for target environment) | `config.rest.security.mode=oidc`  | Comma-separated list of roles with access permissions |
| `config.rest.security.email-claim`                   | `SECURITY_EMAIL_CLAIM`                | `unique_name`                                                  | No (recommended to adjust for target environment) |                                   | JWT claim for user email                              |
| `config.rest.security.disable-swagger-authorization` | `DISABLE_SWAGGER_AUTHORIZATION`       | `false`                                                        | No                                                | -                                 | Disable authorization for Swagger UI                  |

### Identity Providers Configuration

Applied when: config.rest.security.mode=oidc
<br>The configuration is defined in environment variables
<br><br>**Note:** `*` represents a wildcard placeholder, meaning **any provider name**.
<br>**Example:**

- `providers.auth0.issuer`
- `providers.keycloak.client-id`

| Setting                       | Environment Variable (as example) | Required | Applied when                     | Description                                                                                                                     |
|-------------------------------|-----------------------------------|----------|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `providers.*.issuer`          | `providers.azure.issuer`          | Yes      | `config.rest.security.mode=oidc` | List of accepted JWT token issuers for the provider                                                                             |
| `providers.*.jwk-set-uri`     | `providers.azure.jwk-set-uri`     | Yes      | `config.rest.security.mode=oidc` | URI for JSON Web Key Set for the provider                                                                                       |
| `providers.*.aliases`         | `providers.azure.aliases`         | Yes      | `config.rest.security.mode=oidc` | Aliases for accepted JWT token issuers for the provider(only for Azure provider)                                                |
| `providers.*.audiences`       | `providers.azure.audiences`       | Yes      | `config.rest.security.mode=oidc` | List of accepted JWT token audiences. Specifies the intended recipients of the authorization token as defined in its aud claim. |
| `providers.*.role-claims`     | `providers.azure.role-claims`     | No       | `config.rest.security.mode=oidc` | Comma-separated list of JWT claim paths used to extract user roles for the provider.                                            |
| `providers.*.allowed-roles`   | `providers.azure.allowed-roles`   | No       | `config.rest.security.mode=oidc` | Comma-separated list of roles with access permissions for the provider                                                          |
| `providers.*.principal-claim` | `providers.azure.principal-claim` | No       | `config.rest.security.mode=oidc` | Specific claim that uniquely identifies the user or service (the "principal") for whom the token was issued.                    |

**Note:** The configuration for identity providers in the deployment manager utilizes the existing configuration from DIAL admin providers, including settings for clients and roles. 
For detailed instructions on setting up Azure and Keycloak providers, please refer to the DIAL admin providers documentation available at [DIAL Admin Providers Documentation](https://github.com/epam/ai-dial-admin-backend/tree/development/docs).

## Cloud Provider Configuration

### Azure Configuration

| Setting                 | Environment Variable | Default | Required | Applied when                        | Description                                                  |
|-------------------------|----------------------|---------|----------|-------------------------------------|--------------------------------------------------------------|
| azure.auth.type         | AUTH_AZURE_TYPE      | none    | Yes      | -                                   | Azure authentication method (values: credential,cli,managed) |
| azure.auth.clientId     | AZURE_CLIENT_ID      | -       | Yes      | azure.auth.type=credential          | Azure service principal client ID                            |
| azure.auth.tenantId     | AZURE_TENANT_ID      | -       | Yes      | azure.auth.type in [cli,credential] | Azure tenant ID                                              |
| azure.auth.clientSecret | AZURE_CLIENT_SECRET  | -       | Yes      | azure.auth.type=credential          | Azure service principal client secret                        |

## Datasource Configuration

| Setting                        | Environment Variable              | Default                                                                                                              | Required                                          | Applied when                                                   | Description                                                                                                                                            |
|--------------------------------|-----------------------------------|----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| datasource.vendor              | DATASOURCE_VENDOR                 | H2                                                                                                                   | No (recommended to adjust for target environment) | -                                                              | Datasource vendor: <ul><li>H2</li><li>POSTGRES</li><li>MS_SQL_SERVER</li></ul>                                                                         |
| datasource.auth.type           | DATASOURCE_AUTH_TYPE              | basic                                                                                                                | No                                                | -                                                              | Datasource auth type: <ul><li>basic (username and password)</li><li>azure (see [Azure Configuration](#azure-configuration): azure.auth.type)</li></ul> |
| h2.datasource.url              | H2_DATASOURCE_URL                 | jdbc:h2:file:${H2_FILE};${H2_OPS}                                                                                    | No                                                | datasource.vendor=H2                                           | JDBC URL for H2 database connection                                                                                                                    |
|                                | H2_FILE                           | ./data/testdb                                                                                                        | No (recommended to adjust for target environment) | datasource.vendor=H2                                           | H2 database file                                                                                                                                       |
|                                | H2_OPS                            | CIPHER=AES;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE                                                                  | No                                                | datasource.vendor=H2                                           | H2 database connection options                                                                                                                         |
| h2.datasource.masterKey        | H2_DATASOURCE_MASTERKEY           | -                                                                                                                    | Yes                                               | datasource.vendor=H2                                           | Master key for H2 database encryption                                                                                                                  |
| h2.datasource.encryptedFileKey | H2_DATASOURCE_ENCRYPTEDFILEKEY    | -                                                                                                                    | Yes                                               | datasource.vendor=H2                                           | Encrypted file key for H2 database                                                                                                                     |
| h2.datasource.password         | H2_DATASOURCE_PASSWORD            | -                                                                                                                    | Yes                                               | datasource.vendor=H2                                           | Password for H2 database access                                                                                                                        |
| postgres.datasource.url        | POSTGRES_DATASOURCE_URL           | jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DATABASE}?${POSTGRES_OPS:}                            | No                                                | datasource.vendor=POSTGRES                                     | JDBC URL for Postgres database connection                                                                                                              |
|                                | POSTGRES_HOST                     | localhost                                                                                                            | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES                                     | Postgres database host                                                                                                                                 |
|                                | POSTGRES_PORT                     | 5432                                                                                                                 | No                                                | datasource.vendor=POSTGRES                                     | Postgres database port                                                                                                                                 |
|                                | POSTGRES_DATABASE                 | testdb                                                                                                               | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES                                     | Postgres database name                                                                                                                                 |
|                                | POSTGRES_OPS                      | -                                                                                                                    | No                                                | datasource.vendor=POSTGRES                                     | Postgres database connection options                                                                                                                   |
| postgres.datasource.username   | POSTGRES_DATASOURCE_USERNAME      | postgres                                                                                                             | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES and datasource.auth.type=basic      | Username for Postgres database access                                                                                                                  |
| postgres.datasource.password   | POSTGRES_DATASOURCE_PASSWORD      | postgres                                                                                                             | No (recommended to adjust for target environment) | datasource.vendor=POSTGRES and datasource.auth.type=basic      | Password for Postgres database access                                                                                                                  |
| sqlserver.datasource.url       | SQLSERVER_DATASOURCE_URL          | jdbc:sqlserver://${MS_SQL_SERVER_HOST}:${MS_SQL_SERVER_PORT};database=${MS_SQL_SERVER_DATABASE};${MS_SQL_SERVER_OPS} | No                                                | datasource.vendor=MS_SQL_SERVER                                | JDBC URL for MSSQL Server database connection                                                                                                          |
|                                | MS_SQL_SERVER_HOST                | localhost                                                                                                            | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database host                                                                                                                             |
|                                | MS_SQL_SERVER_PORT                | 1433                                                                                                                 | No                                                | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database port                                                                                                                             |
|                                | MS_SQL_SERVER_DATABASE            | testdb                                                                                                               | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database name                                                                                                                             |
|                                | MS_SQL_SERVER_OPS                 | encrypt=false;                                                                                                       | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER                                | MSSQL Server database connection options                                                                                                               |
| sqlserver.datasource.username  | MS_SQL_SERVER_DATASOURCE_USERNAME | sa                                                                                                                   | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER and datasource.auth.type=basic | Username for MSSQL Server database access                                                                                                              |
| sqlserver.datasource.password  | MS_SQL_SERVER_DATASOURCE_PASSWORD | SQLServerPassword1                                                                                                   | No (recommended to adjust for target environment) | datasource.vendor=MS_SQL_SERVER and datasource.auth.type=basic | Password for MSSQL Server database access                                                                                                              |
| spring.jpa.hibernate.ddl-auto  | SPRING_JPA_HIBERNATE_DDL_AUTO     | validate                                                                                                             | No                                                | -                                                              | Hibernate schema generation strategy                                                                                                                   |
| spring.flyway.enabled          | SPRING_FLYWAY_ENABLED             | true                                                                                                                 | No                                                | -                                                              | Enable or disable Flyway database migrations                                                                                                           |

When using MS_SQL_SERVER we recommend to set case-sensitive, accept-sensitive database collation, e.g. SQL_Latin1_General_CP1_CS_AS. See [Collation and Unicode support](https://learn.microsoft.com/en-us/sql/relational-databases/collations/collation-and-unicode-support).

ai-dial-admin-backend/secrets-utils/generate_h2_secrets.sh can help to generate H2_DATASOURCE_MASTERKEY/H2_DATASOURCE_ENCRYPTEDFILEKEY/H2_DATASOURCE_PASSWORD if H2 db is used.

### JVM and Runtime Configuration

| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| - | `DEBUG_OPTS` | `""` | No | - | JVM debug and monitoring options passed to java command (e.g., for debugging, JMX, profiling) |

## Actuator Configuration

| Setting | Environment Variable | Default | Required | Applied when | Description                  |
|---------|---------------------|---------|----------|-----------|------------------------------|
| management.endpoints.web.exposure.include | MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE | prometheus,health | No | - | Actuator endpoints to expose |
| management.endpoint.health.show-details | MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS | always | No | - | Show health information      |
| management.server.port | MANAGEMENT_SERVER_PORT | 9464 | No | - | Actuator endpoints port      |

## Logging Configuration

### Log Levels
| Property                                                             | Environment Variable | Default Value | Required | Applied when | Description |
|----------------------------------------------------------------------|---------------------|---------------|----------|--------------|-------------|
| `spring.logging.level.org.springframework.security`                  | `SPRING_SECURITY_LOG_LEVEL` | `INFO` | No | - | Log level for Spring Security |
| `spring.logging.level.com.epam.aidial.deployment.manager`            | `APP_LOG_LEVEL` | `INFO` | No | - | Log level for application code |
| `spring.logging.level.org.hibernate.SQL`                             | `HIBERNATE_SQL_LOG_LEVEL` | `INFO` | No | - | Log level for Hibernate SQL statements |
| `spring.logging.level.org.hibernate.type.descriptor.sql.BasicBinder` | `HIBERNATE_BINDER_LOG_LEVEL` | `INFO` | No | - | Log level for Hibernate SQL parameter binding |

### Tomcat Access Logs
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `server.tomcat.accesslog.pattern` | - | `"request: method=%m uri=\"%U\" response: statuscode=%s bytes=%b duration=%D(ms) client: remoteip=%a user=%u useragent=\"%{User-Agent}i\""` | No | - | Pattern for Tomcat access logs |
| `server.tomcat.accesslog.enabled` | `TOMCAT_ACCESSLOG_ENABLED` | `false` | No | - | Enable Tomcat access logs |

### Dynamic Logger Configuration
| Property | Environment Variable | Default Value | Required | Applied when | Description |
|----------|---------------------|---------------|----------|--------------|-------------|
| `logger.configuration.path` | `LOG_CONFIG_PATH` | `log-config/logging.levels.json` | No | - | Path to logging configuration file |
| `logger.configuration.interval` | `LOG_CONFIG_INTERVAL` | `10` | No | - | Interval (in seconds) to check for logging configuration changes |

### Method Execution Logging
| Property                                                 | Environment Variable | Default Value | Required | Applied when | Description |
|----------------------------------------------------------|---------------------|---------|----------|--------------|-------------|
| `app.customizable-trace-interceptor.enabled`             | `CUSTOMIZABLE_TRACE_INTERCEPTOR_ENABLED` | `true` | No | - | Enable method execution tracing |
| `app.customizable-trace-interceptor.messages.ENTER`      | `CUSTOMIZABLE_TRACE_INTERCEPTOR_ENTER_MESSAGE` | `'Enter: $[methodName](): $[arguments]'` | No | - | Message template for method entry |
| `app.customizable-trace-interceptor.messages.EXIT`       | `CUSTOMIZABLE_INTERCEPTOR_EXIT_MESSAGE` | `'Exit: $[methodName]() : in $[invocationTime] ms, returnValue: $[returnValue]'` | No | - | Message template for method exit |
| `appcustomizable-trace-interceptor.messages.EXCEPTION`   | `CUSTOMIZABLE_TRACE_INTERCEPTOR_EXCEPTION_MESSAGE` | `'Exception: $[methodName]() : in $[invocationTime] ms'` | No | - | Message template for method exception |
| `app.trace-log-advisor.expression`                       | - | <code>@annotation(logging.configuration.com.epam.aidial.deployment.manager.LogExecution) &#124;&#124; @within(logging.configuration.com.epam.aidial.deployment.manager.LogExecution)</code> | No | - | Expression to determine which methods to trace |

## OpenTelemetry Configuration

| Setting                             | Environment Variable        | Default                         | Required | Applied when | Description                                       |
|-------------------------------------|-----------------------------|---------------------------------|----------|-----------|---------------------------------------------------|
| otel.sdk.disabled                   | OTEL_SDK_DISABLED           | true                            | No       | -         | Disable OpenTelemetry SDK                         |
| otel.service.name                   | OTEL_SERVICE_NAME           | dial-deployment-manager-backend | No       | -         | Service name                                      |
| otel.exporter.otlp.endpoint         | OTEL_EXPORTER_OTLP_ENDPOINT |                                 | Yes      | otel.sdk.disabled=false         | OpenTelemetry collector endpoint                  |
| otel.exporter.otlp.protocol         | OTEL_EXPORTER_OTLP_PROTOCOL |                                 | Yes      | otel.sdk.disabled=false         | Protocol for OpenTelemetry data export            |
| otel.logs.exporter                  | OTEL_LOGS_EXPORTER          | otlp                            | No       | -         | Exporter for application logs                     |
| otel.traces.exporter                | OTEL_TRACES_EXPORTER        | otlp                            | No       | -         | Exporter for distributed traces                   |
| otel.metrics.exporter               | OTEL_METRICS_EXPORTER       | otlp                            | No       | -         | Exporter for application metrics                  |
| otel.resource.attributes            | OTEL_RESOURCE_ATTRIBUTES    |                                 | No       | -         | Key-value pairs to be used as resource attributes |

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

3. **Database Encryption**: The H2 database uses AES encryption. The master key and encrypted file key must be provided for database access. https://github.com/epam/ai-dial-admin-backend/blob/development/secrets-utils/generate_h2_secrets.sh can help to generate H2_DATASOURCE_MASTERKEY/H2_DATASOURCE_ENCRYPTEDFILEKEY/H2_DATASOURCE_PASSWORD if H2 db is used.

4. **Internal API Endpoint**: The /api/internal/v1/deployments endpoint is intentionally left open without security to allow the admin service to access it from within the cluster without service-to-service authentication. This may require modifications to your service mesh or network policies to allow inbound traffic to this path.
