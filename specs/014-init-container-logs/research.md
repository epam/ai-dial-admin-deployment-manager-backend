# Research: Init Container Logs

## R1: How does the Kubernetes API handle init container logs?

**Decision**: Init container logs are retrievable via the same Kubernetes log API as regular containers — by specifying the container name. No special API path is needed.

**Rationale**: The Kubernetes `pods/log` endpoint accepts a `container` query parameter. It works for both regular containers and init containers. For terminated init containers (which is normal — they run to completion), logs are available without the `previous` flag. The `previous` flag is only needed for logs from the *previous* instance of a restarted container.

**Alternatives considered**: None — this is standard Kubernetes behavior.

## R2: How does Fabric8 client support init container log access?

**Decision**: Fabric8 already supports this via the existing `k8sClient.getPodResource(namespace, podName).inContainer(containerName)` pattern. The container name can be any container (init or regular) in the pod.

**Rationale**: The `.inContainer(name)` method on `PodResource` returns a `ContainerResource` that targets any named container. No additional Fabric8 API is needed.

**Alternatives considered**: None — reuse existing pattern.

## R3: Init container statuses live in a separate field

**Decision**: Kubernetes stores init container statuses in `pod.getStatus().getInitContainerStatuses()`, separate from `pod.getStatus().getContainerStatuses()`. The current `findContainerStatus` method only searches `containerStatuses`, so it will not find init containers.

**Rationale**: The code must search both `containerStatuses` and `initContainerStatuses` when looking up container status by name.

## R4: Loggability rules differ for init containers

**Decision**: The current `assertContainerLoggable` method requires a container to be `Running` when `previous=false`, and to have a terminated state when `previous=true`. For init containers, a terminated state is normal (they run to completion), and their logs should be accessible without `previous=true`.

**Rationale**: The Kubernetes log API returns logs for terminated init containers without the `previous` flag. The validation logic needs to distinguish between init containers and regular containers and relax the "must be running" check for terminated init containers.

## R5: Container discovery for pod listing

**Decision**: Extend `PodInfo` / `PodInfoDto` with a list of container info objects. Each entry includes: container name, type (init vs regular), and state (running/waiting/terminated). Container info is extracted from both `pod.getSpec().getContainers()` and `pod.getSpec().getInitContainers()`, with status from `pod.getStatus().getContainerStatuses()` and `pod.getStatus().getInitContainerStatuses()`.

**Rationale**: Users need to discover container names before requesting logs. The pod listing endpoint is the natural place for this information.

**Alternatives considered**: A separate `/containers` endpoint — rejected as over-engineering for read-only metadata that logically belongs with pod info.
