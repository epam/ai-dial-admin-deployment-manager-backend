# Kubernetes layer

The only place in the codebase that may import Fabric8 / `io.kubernetes` API types in non-configuration code (constitution Principle III). See `.specify/memory/constitution.md` for the full architecture; this file lists only what's specific to this layer.

## Layer rules

- Called by `service/` only. MUST NOT be called from `web/` or `dao/`.
- `@LogExecution` MUST be on every `@Component`.
- Polling loops for K8s state are forbidden — use Fabric8 informers (`kubernetes/informer/`).

## Subpackages

| Path | Responsibility |
|---|---|
| `event/` | Watch / stream K8s events to clients (SSE-backed in `service/`) |
| `informer/` | Fabric8 informer registration + handlers; the canonical state-watching mechanism |
| `knative/` | Knative Serving client, manifest generation for serverless workloads |
| `kserve/` | KServe client and manifest generation for inference deployments |
| `nim/` | NVIDIA NIM CRD (`NIMService`) client |

Top-level utilities (`AbstractK8sResourceClient`, `K8sClient`, `JobRunner`, `PodLogReader`, etc.) are shared across subpackages.

## Related specs

- `specs/kubernetes-events/spec.md`
- `specs/kubernetes-manifests/spec.md`
- `specs/kubernetes-cleanup/spec.md`
- `specs/inference-deployments/spec.md`, `specs/nim-deployments/spec.md` for KServe / NIM specifics.
