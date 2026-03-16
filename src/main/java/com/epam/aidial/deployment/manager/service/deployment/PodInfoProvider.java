package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.utils.K8sParseUtils;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Inspects pod status for deployments. Manually constructed per deployment type
 * with strategy lambdas — not a Spring-managed bean.
 */
@Slf4j
public class PodInfoProvider {

    private final Function<String, String> serviceNameResolver;
    private final Function<String, List<Pod>> servicePodsProvider;
    private final Predicate<Pod> podReadinessChecker;

    PodInfoProvider(Function<String, String> serviceNameResolver,
                       Function<String, List<Pod>> servicePodsProvider,
                       Predicate<Pod> podReadinessChecker) {
        this.serviceNameResolver = serviceNameResolver;
        this.servicePodsProvider = servicePodsProvider;
        this.podReadinessChecker = podReadinessChecker;
    }

    List<PodInfo> getActiveInstances(String deploymentId) {
        log.debug("getActiveInstances: deploymentId='{}'", deploymentId);
        return getInstances(deploymentId, podReadinessChecker);
    }

    List<PodInfo> getInstances(String deploymentId) {
        log.debug("getInstances: deploymentId='{}'", deploymentId);
        return getInstances(deploymentId, null);
    }

    private List<PodInfo> getInstances(String deploymentId, Predicate<? super Pod> filter) {
        var serviceName = serviceNameResolver.apply(deploymentId);
        var podList = servicePodsProvider.apply(serviceName);
        var podsStream = podList.stream();
        if (filter != null) {
            podsStream = podsStream.filter(filter);
        }
        return podsStream.map(this::toPodInfo).toList();
    }

    private PodInfo toPodInfo(Pod pod) {
        var containerStatuses = pod.getStatus() != null
                ? pod.getStatus().getContainerStatuses()
                : null;

        var containerInfo = extractContainerInfo(containerStatuses);

        return new PodInfo(
                pod.getMetadata().getName(),
                Instant.parse(pod.getMetadata().getCreationTimestamp()),
                containerInfo.restartCount(),
                containerInfo.lastTerminationReason(),
                containerInfo.lastExitCode(),
                containerInfo.lastSignal(),
                containerInfo.lastFinishedAt()
        );
    }

    private ContainerInfo extractContainerInfo(List<ContainerStatus> containerStatuses) {
        if (CollectionUtils.isEmpty(containerStatuses)) {
            return new ContainerInfo(0, null, null, null, null);
        }

        int totalRestartCount = 0;
        ContainerStateTerminated mostRecentTermination = null;

        for (var containerStatus : containerStatuses) {
            totalRestartCount += containerStatus.getRestartCount() != null ? containerStatus.getRestartCount() : 0;

            // Check both 'state' (current) and 'lastState' (previous)
            // to find the most recent failure event.
            var currentTerminated = getTerminatedState(containerStatus.getState());
            var previousTerminated = getTerminatedState(containerStatus.getLastState());

            // Compare timestamps to find the true "latest" error
            mostRecentTermination = getLaterTermination(mostRecentTermination, currentTerminated);
            mostRecentTermination = getLaterTermination(mostRecentTermination, previousTerminated);
        }

        if (mostRecentTermination != null) {
            return new ContainerInfo(
                    totalRestartCount,
                    mostRecentTermination.getReason(),
                    mostRecentTermination.getExitCode(),
                    mostRecentTermination.getSignal(),
                    K8sParseUtils.parseInstant(mostRecentTermination.getFinishedAt())
            );
        }

        return new ContainerInfo(totalRestartCount, null, null, null, null);
    }

    private ContainerStateTerminated getTerminatedState(ContainerState state) {
        return (state != null) ? state.getTerminated() : null;
    }

    private ContainerStateTerminated getLaterTermination(ContainerStateTerminated currentBest,
                                                         ContainerStateTerminated candidate) {
        if (candidate == null) {
            return currentBest;
        }
        if (currentBest == null) {
            return candidate;
        }

        var t1 = K8sParseUtils.parseInstant(currentBest.getFinishedAt());
        var t2 = K8sParseUtils.parseInstant(candidate.getFinishedAt());
        if (t1 == null) {
            return candidate;
        }
        if (t2 == null) {
            return currentBest;
        }

        return t2.isAfter(t1) ? candidate : currentBest;
    }

    private record ContainerInfo(
            int restartCount,
            String lastTerminationReason,
            Integer lastExitCode,
            Integer lastSignal,
            Instant lastFinishedAt
    ) {
    }
}
