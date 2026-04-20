# Quickstart: NIM KServe Migration

## What changed

NIM deployments now use `inferencePlatform: kserve` instead of `standalone`. This means:

1. **No more nginx ingress** on NIMService resources — Knative Serving handles routing
2. **Knative autoscaling annotations** are set on NIMService metadata for pod scaling
3. **NIM_CACHE_PATH=/tmp** is auto-injected as an environment variable
4. **Scaling configuration** (minReplicas/maxReplicas) from the deployment flows into Knative annotations

## New configuration properties

| Env Var | Default | Description |
|---|---|---|
| `K8S_NIM_AUTOSCALING_CLASS` | `kpa.autoscaling.knative.dev` | Knative autoscaler class |
| `K8S_NIM_AUTOSCALING_METRIC` | `concurrency` | Autoscaling metric type |
| `K8S_NIM_AUTOSCALING_TARGET` | `10` | Target value for the autoscaling metric |

## Verify

```bash
./gradlew testFast
./gradlew checkstyleMain checkstyleTest
```

## Prerequisites

- Knative Serving must be installed in the target K8s cluster
- NIM operator must support `inferencePlatform: kserve`
