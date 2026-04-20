# Data Model: NIM KServe Migration

No database schema changes required. This feature modifies only Kubernetes manifest generation (in-memory objects).

## Modified Entities

### NIMService manifest (Kubernetes CRD — generated in-memory)

**Metadata annotations** (added):
| Annotation | Source | Type |
|---|---|---|
| `autoscaling.knative.dev/class` | Config property `app.nim.deploy.autoscaling-class` | String |
| `autoscaling.knative.dev/metric` | Config property `app.nim.deploy.autoscaling-metric` | String |
| `autoscaling.knative.dev/target` | Config property `app.nim.deploy.autoscaling-target` | String |
| `autoscaling.knative.dev/min-scale` | `Deployment.scaling.minReplicas` | String |
| `autoscaling.knative.dev/max-scale` | `Deployment.scaling.maxReplicas` | String |
| `autoscaling.knative.dev/initial-scale` | `Math.max(scaling.minReplicas, 1)` | String |

**Metadata annotations** (retained):
| Annotation | Source |
|---|---|
| `serving.knative.dev/progress-deadline` | Computed from probe properties + startup timeout |

**Spec field changes**:
| Field | Before | After |
|---|---|---|
| `inferencePlatform` | `standalone` (default) | `kserve` |
| `expose.ingress` | Generated when `useExternalUrl=true` | Removed entirely |
| `expose.router` | Not set (added by NIM operator) | Empty `Router{}` object |
| `env` (NIM_CACHE_PATH) | Not set | Auto-injected: `NIM_CACHE_PATH=/tmp` |

## Existing Entities (unchanged schema)

### Scaling (domain model)
- `minReplicas: int` — maps to `autoscaling.knative.dev/min-scale`
- `maxReplicas: int` — maps to `autoscaling.knative.dev/max-scale`
- `scaleToZeroDelaySeconds: Integer` (nullable) — maps to `autoscaling.knative.dev/scale-to-zero-pod-retention-period`
- `strategy: ScalingStrategy` — not directly used for NIM (class/metric/target come from config)

### NimDeployProperties (configuration)
New fields:
- `autoscalingClass: String` — default: `kpa.autoscaling.knative.dev`
- `autoscalingMetric: String` — default: `concurrency`
- `autoscalingTarget: int` — default: `10`
