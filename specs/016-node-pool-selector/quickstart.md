# Quickstart: Node Pool Selector

## Prerequisites

- Java 21
- Gradle 8.13
- Running Kubernetes cluster (or minikube/kind for local dev)
- Nodes with labels matching configured pools (e.g., `node-pool=gpu-a100`)

## Configuration

Add node pools to `application.yml`:

```yaml
app:
  node-pools:
    - name: gpu-a100-pool
      description: "NVIDIA A100 80 GB SXM"
      max-nodes: 8
      label-selector:
        node-pool: gpu-a100
      node-spec:
        cpu-millis: 96000
        memory-bytes: 687194767360
        gpu: 3
```

Environment variable overrides follow Spring Boot relaxed binding:
- `APP_NODE_POOLS_0_NAME=gpu-a100-pool`
- `APP_NODE_POOLS_0_MAX_NODES=8`
- `APP_NODE_POOLS_0_LABEL_SELECTOR_NODE_POOL=gpu-a100`
- etc.

## Verify

```bash
# Run the app
./gradlew bootRun

# List node pools with live utilization
curl http://localhost:8080/api/v1/node-pools | jq .

# Create a deployment with node pool
curl -X POST http://localhost:8080/api/v1/deployments \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "test-deploy",
    "displayName": "Test",
    "type": "MCP",
    "source": {"$type": "image_reference", "imageReference": "nginx:latest"},
    "metadata": {"envs": []},
    "nodePool": "gpu-a100-pool"
  }'

# Verify node pool persisted
curl http://localhost:8080/api/v1/deployments/test-deploy | jq .nodePool
```

## Testing

```bash
# Fast tests (H2, no containers)
./gradlew testFast

# Full suite (Postgres + SQL Server via Testcontainers)
./gradlew test

# Run specific test
./gradlew testFast --tests "com.epam.aidial.deployment.manager.functional.h2.NodePoolFunctionalTest"
```

## Key Files

| Purpose | Path |
|---------|------|
| Node pool config | `src/main/java/.../configuration/NodePoolProperties.java` |
| API controller | `src/main/java/.../web/controller/NodePoolController.java` |
| Service logic | `src/main/java/.../service/nodepool/NodePoolService.java` |
| K8s node queries | `src/main/java/.../kubernetes/K8sClient.java` |
| Affinity injection | `src/main/java/.../service/manifest/*ManifestGenerator.java` |
| DB migration | `src/main/resources/db/migration/*/V1.57__AddNodePoolColumn.sql` |
| API tests | `src/test/java/.../functional/h2/NodePoolFunctionalTest.java` |
