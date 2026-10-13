# NIM Deployments

Status: **Removed** (0.22.0)

NVIDIA NIM deployment support (deployment type `NIM`, source type `ngc_registry`, the `NIMService` CRD integration and all `K8S_NIM_*` / `NIM_SERVICE_*` configuration) was removed in 0.22.0 because NIM models are no longer used. Migration `V1.60__RemoveNimDeployment` deletes existing NIM deployments together with their topics, audit history and activity feed entries, and drops the `nim_deployment` / `nim_deployment_aud` tables. Export archives from earlier releases still import; their `nimDeployments` block is ignored.

Removed via [028-remove-nim-deployments](../028-remove-nim-deployments/spec.md). Upgrade steps: `docs/upgrade-plans/0.22.0.md`.

Historical feature specs: [012-nim-url-schema](../012-nim-url-schema/spec.md), [013-nim-served-model-name](../013-nim-served-model-name/spec.md), [015-nim-kserve-migration](../015-nim-kserve-migration/spec.md).
