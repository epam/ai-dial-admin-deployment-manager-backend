# Quickstart: Node Pool Selector

## Prerequisites

- Java 21
- Gradle 8.13
- Running Kubernetes cluster (or minikube/kind for local dev)
- Nodes with labels matching configured pools (e.g., `node-pool=gpu-a100-prod`)

## Configuration

Set environment variables:

```bash
# Kubernetes node label key used to identify pools (default: node-pool)
NODE_POOL_LABEL_KEY=node-pool

# JSON array of node pool configurations
NODE_POOLS='[{"name":"gpu-a100-prod","description":"LLM inference & fine-tuning","instance":"a2-ultragpu-4g","maxNodes":10,"gpu":{"name":"NVIDIA A100","vramBytes":85899345920,"count":4},"cpu":{"name":"AMD EPYC Milan","milliCpus":48000},"memory":{"bytes":730144440320}}]'
```

Nodes must be labeled to match: `kubectl label node <node> node-pool=gpu-a100-prod`

## Verify

```bash
# Run the app
./gradlew bootRun

# List node pools
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
    "nodePool": "gpu-a100-prod"
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
```

## Key Files

| Purpose | Path |
|---------|------|
| Config properties | `src/main/java/.../configuration/NodePoolProperties.java` |
| Config parsing + validation | `src/main/java/.../configuration/NodePoolConfiguration.java` |
| API controller | `src/main/java/.../web/controller/NodePoolController.java` |
| Service logic | `src/main/java/.../service/nodepool/NodePoolService.java` |
| Affinity injection | `src/main/java/.../service/manifest/*ManifestGenerator.java` |
| DB migration | `src/main/resources/db/migration/*/V1.58__AddNodePoolColumn.sql` |
