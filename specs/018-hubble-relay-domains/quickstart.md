# Quickstart: Hubble Relay Domain Streaming

## Prerequisites

1. **Hubble Relay installed in the untrusted cluster**: The Cilium `hubble-relay` pod must be running in the `cilium` namespace and accessible via gRPC on port 80 (default ClusterIP).

2. **RBAC: `pods/portforward` permission on the `cilium` namespace**: The Deployment Manager service account on the untrusted cluster must have the following permission in addition to existing Cilium RBAC (see `specs/cilium/spec.md`):

   ```yaml
   rules:
     - apiGroups: [""]
       resources: ["pods/portforward"]
       verbs: ["create"]
   ```

3. **`CILIUM_NETWORK_POLICIES_ENABLED=true`**: Hubble Relay captures DNS flows enforced by Cilium network policies. If Cilium policies are disabled (`CILIUM_NETWORK_POLICIES_ENABLED=false`), no DNS flows will be generated and domain streaming will produce no events. The Deployment Manager will log a warning and skip domain streaming in this case.

4. **Network policies allow DNS L7 inspection**: Cilium must be enforcing L7 DNS proxy policies on the build and deployment pods. Without this, no DNS flows will be visible to Hubble Relay.

---

## Configuration

Set the following environment variables in the Deployment Manager deployment:

| Environment Variable | Default | Description |
|---------------------|---------|-------------|
| `HUBBLE_RELAY_ENABLED` | `false` | Master switch. Set to `true` to enable Hubble Relay integration. Requires `CILIUM_NETWORK_POLICIES_ENABLED=true`. |
| `HUBBLE_RELAY_HOST` | `hubble-relay.cilium.svc.cluster.local` | Hubble Relay gRPC host. Used by the direct-connect path (NodePort/LoadBalancer). Not used with the default port-forward approach (which connects to `localhost:localPort`), but must be set correctly when upgrading to NodePort/LB connectivity. |
| `HUBBLE_RELAY_NAMESPACE` | `cilium` | Kubernetes namespace where the `hubble-relay` pod runs in the untrusted cluster. Used by the port-forward approach to locate the relay pod. |
| `HUBBLE_RELAY_POD_LABEL_SELECTOR` | `k8s-app=hubble-relay` | Label selector used to find the `hubble-relay` pod for port-forwarding. |
| `HUBBLE_RELAY_PORT` | `80` | gRPC port on the Hubble Relay pod. |
| `HUBBLE_RELAY_CONNECT_RETRY_COUNT` | `3` | Number of connection attempts before degrading gracefully. |
| `HUBBLE_RELAY_CONNECT_RETRY_INTERVAL_MS` | `2000` | Milliseconds between connection retry attempts. |
| `HUBBLE_RELAY_TLS_ENABLED` | `false` | Enable gRPC-level TLS. Set to `true` when using NodePort or LoadBalancer connectivity (not needed for port-forward). |
| `HUBBLE_RELAY_CA_CERT_PATH` | _(empty)_ | Path to the CA certificate file used to validate the Hubble Relay server certificate. Required when `HUBBLE_RELAY_TLS_ENABLED=true`. |

> **Note on TLS**: With the default port-forward approach, `HUBBLE_RELAY_TLS_ENABLED` must remain `false` — the gRPC channel is `localhost` plaintext; transport security is provided by the Kubernetes API TLS + RBAC layer. Enable TLS only when upgrading to NodePort or LoadBalancer connectivity.

---

## Minimal `application.yml` defaults

```yaml
hubble-relay:
  enabled: ${HUBBLE_RELAY_ENABLED:false}
  host: ${HUBBLE_RELAY_HOST:hubble-relay.cilium.svc.cluster.local}
  namespace: ${HUBBLE_RELAY_NAMESPACE:cilium}
  pod-label-selector: ${HUBBLE_RELAY_POD_LABEL_SELECTOR:k8s-app=hubble-relay}
  port: ${HUBBLE_RELAY_PORT:80}
  connect-retry-count: ${HUBBLE_RELAY_CONNECT_RETRY_COUNT:3}
  connect-retry-interval-ms: ${HUBBLE_RELAY_CONNECT_RETRY_INTERVAL_MS:2000}
  tls-enabled: ${HUBBLE_RELAY_TLS_ENABLED:false}
  ca-cert-path: ${HUBBLE_RELAY_CA_CERT_PATH:}
```

---

## Verification

### 1. Trigger a build

```bash
curl -X POST https://<host>/api/v1/images/builds \
  -H "Content-Type: application/json" \
  -d '{"imageDefinitionId": "<uuid>"}'
```

### 2. Open the build log stream

```bash
curl -N https://<host>/api/v1/images/builds/<imageDefinitionId>/logs \
  -H "Accept: text/event-stream"
```

Expected interleaved output (Hubble Relay enabled):

```
event: logs
data: [2026-04-24T10:25:01Z] Step 1/4: FROM python:3.11-slim

event: domain
data: {"domain":"auth.docker.io","verdict":"ALLOWED"}

event: domain
data: {"domain":"registry-1.docker.io","verdict":"ALLOWED"}

event: status
data: RUNNING
```

### 3. Verify build details

After build completion:

```bash
curl https://<host>/api/v1/images/builds/<imageDefinitionId>/details
```

Expected `domains` field in the response:

```json
{
  "domains": [
    { "domain": "auth.docker.io", "verdict": "ALLOWED" },
    { "domain": "registry-1.docker.io", "verdict": "ALLOWED" }
  ]
}
```

### 4. Open a deployment pod log stream

```bash
curl -N "https://<host>/api/v1/deployments/<deploymentId>/pods/<podName>/logs" \
  -H "Accept: text/event-stream"
```

Expected `domain` events interleaved with `logs` events when the pod contacts external services.

---

## Degraded Mode (Hubble Relay Unavailable)

If the Hubble Relay pod cannot be reached after all retries:
- A warning is logged: `Hubble Relay connection failed after N retries for scope <scope>. Domain streaming disabled for this run.`
- The build or deployment **proceeds normally** — no failure, no `domain` events emitted.
- `domains` in the build details response will be `null` or empty.

---

## Disabling the Feature

Set `HUBBLE_RELAY_ENABLED=false` (the default). All build and deployment behaviour is identical to the pre-feature baseline.
