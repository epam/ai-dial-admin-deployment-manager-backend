# Cilium

## Purpose

This spec describes how the Deployment Manager integrates with Cilium — an eBPF-based Kubernetes networking layer — to enforce network policies for image build jobs and running deployments. It also covers **Hubble Relay** (Hubble Relay), what it is, and how the system should integrate with it for network observability.

Status: **Partially implemented** (network policy enforcement implemented; Hubble Relay integration not yet implemented)

---

## Key Terms

- **Cilium**: An eBPF-based Kubernetes CNI plugin that provides L3/L4/L7 network policies, transparent encryption, and observability. Cilium is installed **only on the untrusted cluster** (the cluster where image build pods and deployment pods run). An image build pod uses BuildKit to build and push a Docker image; a deployment pod runs the Docker image produced by that process. Cilium enforces network policies on both pod types. The trusted cluster — where the Deployment Manager itself runs — does **not** have Cilium installed.
- **CiliumNetworkPolicy (CNP)**: A namespaced Kubernetes custom resource (`ciliumnetworkpolicies.cilium.io`) that defines network ingress and egress rules for pods selected by label. The Deployment Manager creates, updates, and deletes CNPs as part of the build and deploy lifecycle.
- **CiliumClusterwideNetworkPolicy**: A cluster-scoped variant of `CiliumNetworkPolicy`. The Deployment Manager holds RBAC permission to manage both; current code uses only the namespaced variant.
- **Egress rule**: A CNP rule that controls which destinations a pod may contact (external FQDNs, Kubernetes endpoints, or entity groups like `world`).
- **Ingress rule**: A CNP rule that controls which sources may connect to the selected pods.
- **FQDN matching**: A Cilium-specific DNS-name-based selector (e.g., `matchName: "huggingface.co"`) applied to L7-visible DNS lookups, enabling domain-level egress control.
- **ToEntities/WORLD**: A Cilium shorthand that allows egress to any external (non-cluster) destination; used when the allowed-domains list contains the wildcard value `"*"`.
- **Hubble**: Cilium's built-in network observability platform. Captures and exposes all network flows (L3/L4/L7) processed by the Cilium dataplane.
- **Hubble Relay**: A Hubble component that aggregates flow data from all Cilium agents across cluster nodes and exposes a single gRPC API endpoint (`observer.Observer`). Runs as a Kubernetes `Service` (typically `hubble-relay.cilium:80`). The Deployment Manager can query it to observe which external domains image build pods and deployment pods accessed — and whether those accesses were allowed or blocked — without touching the Cilium dataplane directly.

---

## Part I: Network Policy Enforcement (Implemented)

> **Cluster scope**: All Cilium resources (`CiliumNetworkPolicy`, etc.) are created and managed on the **untrusted cluster** only. The Deployment Manager runs on the trusted cluster and manages these resources remotely via the cross-cluster Kubernetes API connection.

### Requirement: Cilium network policies are optional and gated by configuration

Cilium network policy management is controlled by a single feature flag. When disabled, no CNP resources are created, updated, or deleted, and all build and deploy operations proceed without any Cilium interaction.

Status: **Implemented**

#### Scenario: Cilium disabled (default)
- **WHEN** `CILIUM_NETWORK_POLICIES_ENABLED=false` (or absent)
- **THEN** `CiliumNetworkPolicyCreator.isCiliumNetworkPoliciesEnabled()` returns `false`; no `CiliumNetworkPolicy` resources are created, updated, or deleted for any build or deployment operation

#### Scenario: Cilium enabled
- **WHEN** `CILIUM_NETWORK_POLICIES_ENABLED=true`
- **THEN** a `CiliumNetworkPolicy` is created for every image-build job and every activated deployment; policies are kept in sync with domain and port changes

---

### Requirement: CiliumNetworkPolicy created for image build jobs

When Cilium is enabled, a `CiliumNetworkPolicy` is created in the build namespace alongside each image-build Kubernetes `Job`. The policy restricts outbound network access to only the allowed domains for that specific build.

Status: **Implemented**

#### Scenario: Build job with allowed domains
- **WHEN** an image build is triggered and Cilium is enabled and the build has a non-empty effective allowed-domain list (union of global whitelist and per-image-definition `allowedDomains`)
- **THEN** a `CiliumNetworkPolicy` is created in the build namespace with egress rules permitting TCP 443/80 to the specified FQDNs, plus kube-dns UDP 53 egress; the CNP is registered as a disposable resource for automatic cleanup

#### Scenario: Build job with wildcard allowed domains
- **WHEN** the effective allowed-domain list contains `"*"`
- **THEN** the CNP's egress uses `ToEntities: [world]` instead of FQDN selectors, permitting outbound access to any external destination on TCP 443/80

#### Scenario: Build job with no allowed domains
- **WHEN** the effective allowed-domain list is empty
- **THEN** no egress rules are added to the CNP; the pod cannot make outbound connections (except for DNS resolution rules if the list is non-empty)

#### Scenario: CNP cleaned up after build completes
- **WHEN** an image build pod reaches a terminal state
- **THEN** the associated `CiliumNetworkPolicy` is marked `TO_CLEANUP` by the disposable resource machinery and deleted on the next cleanup run

---

### Requirement: CiliumNetworkPolicy created for deployments on deploy

When Cilium is enabled, a `CiliumNetworkPolicy` is created in the deployment namespace when a deployment is activated (`POST /api/v1/deployments/{id}/deploy`).

Status: **Implemented**

#### Scenario: Deployment activated with Cilium enabled
- **WHEN** `POST /api/v1/deployments/{id}/deploy` is called and Cilium is enabled
- **THEN** a `CiliumNetworkPolicy` is created in the deployment namespace before the Kubernetes service is created; the CNP is registered as a disposable resource

#### Scenario: Unrecoverable error creating CNP marks deployment STOPPED
- **WHEN** `CiliumNetworkPolicy` creation fails with a 401, 403, or 404 Kubernetes response
- **THEN** the deployment is marked `STOPPED` and disposable resources are scheduled for cleanup; a `DeploymentException` is thrown

---

### Requirement: CiliumNetworkPolicy updated when deployment configuration changes

When a running deployment's `allowedDomains` or `containerPort` change, the corresponding `CiliumNetworkPolicy` is updated in-place without restarting the deployment.

Status: **Implemented**

#### Scenario: Domain list updated for running deployment
- **WHEN** `PUT /api/v1/deployments/{id}` changes `allowedDomains` and the deployment is `RUNNING`
- **THEN** `DeploymentService` calls `deploymentManager.updateCiliumNetworkPolicy(id)`, which rebuilds the CNP spec and applies it via `k8sClient.updateCiliumNetworkPolicy`; the Knative/NIM/KServe service is **not** restarted

---

### Requirement: CiliumNetworkPolicy deleted on undeploy

When a deployment is undeployed, its `CiliumNetworkPolicy` is deleted alongside the Kubernetes service resources.

Status: **Implemented**

#### Scenario: Undeploy with Cilium enabled
- **WHEN** `POST /api/v1/deployments/{id}/undeploy` is called and Cilium is enabled
- **THEN** the CNP is deleted in the same `afterCommit` block as the Kubernetes service; a 404 response from the Kubernetes API is treated as "already deleted" and does not fail the undeploy

#### Scenario: Undeploy with Cilium disabled
- **WHEN** Cilium is disabled but a CNP exists from a prior enabled state
- **THEN** deletion failures are logged at DEBUG level and do not propagate; the undeploy completes successfully

---

### Policy structure

Every `CiliumNetworkPolicy` created by the Deployment Manager has the following structure:

**EndpointSelector**: selects pods by the deployment's Kubernetes service-name label (e.g., `app: dm-abc123` for Knative, or the equivalent label for NIM/KServe).

**Egress** (when `allowedDomains` is non-empty):

| Rule | Purpose |
|---|---|
| `ToFQDNs: [{matchName: "<domain>"}]` (one per domain) + `ToPorts: [TCP/443, TCP/80]` | Allow HTTPS and HTTP outbound to each whitelisted domain |
| `ToEntities: [world]` + `ToPorts: [TCP/443, TCP/80]` | Used instead of FQDN rules when `allowedDomains` contains `"*"` |
| `ToEndpoints: [{k8s:k8s-app: kube-dns, k8s:io.kubernetes.pod.namespace: kube-system}]` + `ToPorts: [UDP+TCP/53]` + DNS rules | Allow DNS resolution of whitelisted names (or `matchPattern: "*"` for wildcard) |

**Ingress**:

| Rule | Purpose |
|---|---|
| `FromEndpoints: [istio-ingressgateway, knative activator, knative autoscaler]` | Allow inbound traffic from the Istio ingress gateway and Knative internal components |
| `ToPorts: [TCP/8012, TCP/8022, TCP/<containerPort>]` | Permit the specific ports; NIM deployments also include `containerGrpcPort` |

**Policy naming**: The CNP name equals the deployment's `serviceName` (e.g., `dm-abc123`). The disposable resource table is the source of truth for the actual CNP name (provides backward compatibility with pre-v1.53 double-prefixed names like `dm-dm-abc123`).

---

### HuggingFace default allowed domains

For Inference deployments with a `HuggingFaceSource`, a configurable list of default domains is automatically merged into the deployment's effective `allowedDomains` list before the CNP is created or updated. This ensures HuggingFace model downloads are permitted without requiring operators to manually add these domains to every inference deployment.

| Property | Env Var | Default |
|---|---|---|
| `app.huggingface.default-allowed-domains` | `HUGGINGFACE_DEFAULT_ALLOWED_DOMAINS` | `huggingface.co,transfer.xethub.hf.co,cas-server.xethub.hf.co` |

---

### RBAC requirements

The `dial-deployment-manager` ServiceAccount requires the following permissions in **each** target namespace when Cilium is enabled:

```
apiGroups: [cilium.io]
resources: [ciliumnetworkpolicies, ciliumclusterwidenetworkpolicies]
verbs: [create, update, patch, get, delete, deletecollection, list, watch]

apiGroups: [cilium.io]
resources: [ciliumnetworkpolicies/status, ciliumclusterwidenetworkpolicies/status]
verbs: [patch, update]
```

Apply these in: `K8S_BUILD_NAMESPACE`, `K8S_KNATIVE_DEPLOYMENT_NAMESPACE`, `K8S_NIM_DEPLOYMENT_NAMESPACE`, `K8S_KSERVE_DEPLOYMENT_NAMESPACE`. See `docs/deployment_guide_full_security_model.md` for the full RBAC manifest.

---

### Configuration

| Property | Env Var | Default | Description |
|---|---|---|---|
| `app.cilium-network-policies-enabled` | `CILIUM_NETWORK_POLICIES_ENABLED` | `false` | Master switch for all Cilium network policy management. Set to `true` only when Cilium is installed on the target cluster. |

---

## Part II: Hubble Relay — Observability Integration

### What is Hubble Relay?

**Hubble Relay** is the gRPC API gateway for **Hubble**, Cilium's built-in network observability platform. Hubble instruments the Cilium eBPF dataplane to record every network flow processed in the cluster — including source pod, destination FQDN/IP/port, L7 protocol details, and the **verdict** (`FORWARDED` / `DROPPED` / `REDIRECTED`).

Individual Cilium agents export flow data locally. **Hubble Relay** (also called `hubble-relay`) aggregates flows from all agents into a single gRPC endpoint using the `observer.ObserverService` Protocol Buffer API. It runs as a Kubernetes `Deployment` in the `cilium` namespace of the **untrusted cluster** and is exposed via a `ClusterIP` service on port **80** (gRPC). Because Hubble Relay is in the untrusted cluster and the Deployment Manager runs in the trusted cluster, the connection crosses the cluster boundary (see connection options below).

Clients connect to `hubble-relay.cilium.svc.cluster.local:80` (or the configured service address) and call:

- `GetFlows(GetFlowsRequest)` — returns a stream of `GetFlowsResponse` messages, each containing one `Flow` with: `source` pod, `destination` (IP, port, DNS name), `verdict`, `drop_reason`, `l7` details (HTTP method/status for HTTP/gRPC flows), and `time`.
- `GetNodes(GetNodesRequest)` — returns the list of Hubble-enabled nodes with connection status.

Flow filters allow scoping by namespace, pod label, verdict, destination FQDN, and time range.

> **Installation note**: Hubble and Hubble Relay are optional Cilium components enabled at cluster install time via `--set hubble.enabled=true --set hubble.relay.enabled=true` (Helm). They must be enabled separately from Cilium itself. See the [official Hubble docs](https://docs.cilium.io/en/stable/gettingstarted/hubble_setup/).

---

### Why integrate with Hubble Relay?

When `CILIUM_NETWORK_POLICIES_ENABLED=true`, the Deployment Manager enforces which external domains image build pods and deployment pods may contact. However, operators currently have no visibility into:

- Which domains an image build pod **actually accessed** during the build
- Which domains were **blocked** by the Cilium policy (causing silent build failures or missing packages)
- Which domains a running deployment contacts at runtime

Integrating with Hubble Relay lets the Deployment Manager **observe** real network flows in addition to **enforcing** policies, enabling domain-access auditing without requiring operators to manually inspect Cilium or Hubble CLI output.

---

### Integration approach

#### Cross-cluster connection

Hubble Relay runs on the **untrusted cluster** as a `ClusterIP` service — it is not directly reachable from the trusted cluster without additional connectivity. Viable options include:

- **Kubernetes API port-forward** (via fabric8 `LocalPortForward`): tunnels gRPC through the existing cross-cluster K8s API WebSocket. No infrastructure changes required; adds API-server overhead and requires reconnect handling for long-lived streams.
- **NodePort service + firewall rule**: expose `hubble-relay` as a NodePort in the untrusted cluster and open a firewall rule from the trusted cluster. Direct gRPC connection, simpler code, but requires infrastructure coordination.
- **LoadBalancer service**: cloud-provisioned external IP/DNS for `hubble-relay`. Stable and production-grade; higher infrastructure cost.

The chosen mechanism is transparent to the application — only the configured `HUBBLE_RELAY_HOST`/`HUBBLE_RELAY_PORT` values change.

#### Connection configuration

The Deployment Manager connects to Hubble Relay via gRPC. The service address is configurable:

| Property | Env Var | Default | Description |
|---|---|---|---|
| `app.hubble-relay.enabled` | `HUBBLE_RELAY_ENABLED` | `false` | Enable Hubble Relay integration. Requires `CILIUM_NETWORK_POLICIES_ENABLED=true`. |
| `app.hubble-relay.host` | `HUBBLE_RELAY_HOST` | `hubble-relay.cilium.svc.cluster.local` | Hubble Relay gRPC host |
| `app.hubble-relay.port` | `HUBBLE_RELAY_PORT` | `80` | Hubble Relay gRPC port |
| `app.hubble-relay.tls-enabled` | `HUBBLE_RELAY_TLS_ENABLED` | `false` | Enable TLS for the gRPC connection |

The gRPC channel is created at startup (lazy or eager, depending on the guard flag) and reused for all flow queries.

#### RBAC requirements for Hubble Relay

No additional Kubernetes RBAC is needed — the Hubble Relay gRPC endpoint is not a Kubernetes API resource. Access is controlled at the network level (the Deployment Manager pod must be able to reach the Relay service) and optionally via mTLS/TLS configuration on the Relay side.

If the Relay is deployed in a namespace protected by its own Cilium policy, an egress rule from the Deployment Manager pod to `hubble-relay.cilium:80` (TCP/gRPC) must exist in the Deployment Manager's own `CiliumNetworkPolicy`.

#### Flow observation for image build pods

When a build job completes (or while it is running), the Deployment Manager queries Hubble Relay for all flows originating from pods with `job-name=<build-job-name>` in the build namespace. The query window covers the job's lifetime (`sinceTime` = job start, `untilTime` = job completion or now).

Each returned flow that has a DNS destination is mapped to a domain-access entry:

| Flow field | Mapped to |
|---|---|
| `destination.names[0]` (DNS name from Hubble's FQDN tracking) | domain name |
| `verdict` | `ALLOWED` (`FORWARDED`) or `BLOCKED` (`DROPPED`) |

The collected entries are de-duplicated per (domain, verdict) pair and stored for the build's `imageDefinitionId`.

#### Flow observation for running deployments

For running deployments, the Deployment Manager can maintain a live gRPC stream from Hubble Relay filtered to pods with the deployment's service-name label in the relevant namespace. Domain-access events are streamed to connected SSE clients in real time and persisted for later retrieval.

#### SSE event type

Both build and deployment domain observations are exposed as a new SSE event type `domain`:

```
event: domain
data: {"domain":"huggingface.co","verdict":"ALLOWED"}
```

Emitted in the existing log/status SSE streams alongside `logs` and `status` events.

---

### Requirement: Observe build-pod domain access via Hubble Relay

Status: **Not implemented**

When Hubble Relay integration is enabled, the system SHALL query Hubble Relay after (or during) an image build to collect all external domains contacted by the image build pod and their Cilium verdicts.

#### Scenario: Domain access captured for build
- **WHEN** Hubble Relay is enabled and a build completes
- **THEN** the system queries Hubble Relay for flows from the image build pod and stores a deduplicated list of (domain, verdict) pairs against the `imageDefinitionId`

#### Scenario: Blocked domain visible in build details
- **WHEN** an image build pod attempts to access a domain not in the allowed list and the access is blocked by Cilium
- **THEN** that domain appears in the build's domain list with verdict `BLOCKED`

#### Scenario: Domain events streamed in real time
- **WHEN** a client is connected to the build log SSE stream and Hubble Relay is enabled
- **THEN** `domain` events are pushed as new domain accesses are observed from the Hubble Relay flow stream

#### Scenario: Hubble Relay disabled — no domain events
- **WHEN** `HUBBLE_RELAY_ENABLED=false`
- **THEN** no `domain` SSE events are emitted and the `domains` field in build details is empty or absent

---

### Requirement: Observe deployment domain access via Hubble Relay

Status: **Not implemented**

When Hubble Relay integration is enabled, the system SHALL maintain a live stream of domain accesses for each running deployment and expose them via SSE.

#### Scenario: Domain access streamed for running deployment
- **WHEN** Hubble Relay is enabled and a deployment is `RUNNING`
- **THEN** real-time `domain` events are available on the deployment's event stream; each event includes the accessed domain and verdict

#### Scenario: Historical domain access available after undeploy
- **WHEN** a deployment is undeployed and Hubble Relay was enabled during its runtime
- **THEN** the collected domain access entries remain queryable in the deployment's details until the record is deleted

---

## Implementation Notes

- Network policy creator: `com.epam.aidial.deployment.manager.service.pipeline.specification.CiliumNetworkPolicyCreator`
- K8s operations: `com.epam.aidial.deployment.manager.kubernetes.K8sClient` — `createCiliumNetworkPolicy`, `updateCiliumNetworkPolicy`, `deleteCiliumNetworkPolicy`
- Deployment lifecycle integration: `com.epam.aidial.deployment.manager.service.deployment.AbstractDeploymentManager` — `createCiliumNetworkPolicy`, `updateCiliumNetworkPolicy`, `deleteCiliumNetworkPolicy`, `resolveCiliumNetworkPolicyName`, `getCiliumIngressPorts`
- NIM port override: `NimDeploymentManager.getCiliumIngressPorts` — adds `containerGrpcPort` to ingress port set
- Build job integration: `com.epam.aidial.deployment.manager.kubernetes.JobRunner` — merges global whitelist + per-definition `allowedDomains`, calls `CiliumNetworkPolicyCreator.create`
- HuggingFace default domains: `com.epam.aidial.deployment.manager.configuration.HuggingFaceProperties.defaultAllowedDomains`; merged in `InferenceDeploymentManager.getEffectiveDeploymentAllowedDomains`
- Disposable resource type: `K8sResourceKind.CILIUM_NETWORK_POLICY`; cleanup order: after `INFERENCE_SERVICE`, before `IMAGE`
- CRD snapshot (Fabric8 schema registration): `src/main/resources/kubernetes/crd/ciliumnetworkpolicy.crd.yaml`
- CNP backward-compat naming: pre-v1.53 used double-prefixed names (e.g., `dm-dm-abc123`); current code uses `serviceName` directly; `resolveCiliumNetworkPolicyName` looks up the actual name from the disposable resource table
- Hubble Relay gRPC API: Protocol Buffers defined in [cilium/cilium api/v1](https://github.com/cilium/hubble/tree/main/vendor/github.com/cilium/cilium/api/v1/observer); main service: `observer.Observer.GetFlows`
- Related specs: `domain-whitelist` (allowed domain configuration), `buildkit` (image build pod lifecycle), `deployments` (deployment pod lifecycle), `kubernetes-cleanup` (disposable resource cleanup)
