package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.PodInfo;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PodStatusInspectorTest {

    private static final String DEPLOYMENT_ID = "test-deployment";
    private static final String SERVICE_NAME = "test-service";
    private static final String CONTAINER_NAME = "main";

    private PodStatusInspector podStatusInspector;
    private List<Pod> podList;

    @BeforeEach
    void setUp() {
        podList = Collections.emptyList();
        podStatusInspector = new PodStatusInspector(
                deploymentId -> SERVICE_NAME,
                serviceName -> podList,
                pod -> {
                    var status = pod.getStatus();
                    if (status == null || status.getContainerStatuses() == null) {
                        return false;
                    }
                    return status.getContainerStatuses().stream()
                            .anyMatch(cs -> cs.getState() != null && cs.getState().getRunning() != null);
                }
        );
    }

    @Test
    void shouldReturnActiveInstances() {
        var readyPod = createPod("ready-pod", true);
        var notReadyPod = createPod("not-ready-pod", false);
        podList = List.of(readyPod, notReadyPod);

        List<PodInfo> result = podStatusInspector.getActiveInstances(DEPLOYMENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getName()).isEqualTo("ready-pod");
    }

    @Test
    void shouldReturnAllInstances() {
        var readyPod = createPod("ready-pod", true);
        var notReadyPod = createPod("not-ready-pod", false);
        podList = List.of(readyPod, notReadyPod);

        List<PodInfo> result = podStatusInspector.getInstances(DEPLOYMENT_ID);

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldExtractContainerInfoWithRestarts() {
        var pod = createPodWithTermination("restarting-pod", 3, "OOMKilled", 137, 9);
        podList = List.of(pod);

        List<PodInfo> result = podStatusInspector.getInstances(DEPLOYMENT_ID);

        assertThat(result).hasSize(1);
        var podInfo = result.getFirst();
        assertThat(podInfo.getRestartCount()).isEqualTo(3);
        assertThat(podInfo.getLastTerminationReason()).isEqualTo("OOMKilled");
        assertThat(podInfo.getLastExitCode()).isEqualTo(137);
        assertThat(podInfo.getLastSignal()).isEqualTo(9);
        assertThat(podInfo.getLastFinishedAt()).isNotNull();
    }

    @Test
    void shouldHandleTerminationStateComparison() {
        // Pod with two containers, each having a different terminated time
        var pod = createPodWithMultipleTerminations("multi-term-pod");
        podList = List.of(pod);

        List<PodInfo> result = podStatusInspector.getInstances(DEPLOYMENT_ID);

        assertThat(result).hasSize(1);
        var podInfo = result.getFirst();
        // The later termination should be selected
        assertThat(podInfo.getLastTerminationReason()).isEqualTo("LaterReason");
    }

    @Test
    void shouldReturnEmptyListWhenNoPods() {
        podList = Collections.emptyList();

        List<PodInfo> result = podStatusInspector.getActiveInstances(DEPLOYMENT_ID);

        assertThat(result).isEmpty();
    }

    private Pod createPod(String name, boolean ready) {
        var pod = new Pod();
        var metadata = new ObjectMeta();
        metadata.setName(name);
        metadata.setCreationTimestamp(Instant.now().toString());
        pod.setMetadata(metadata);

        var podStatus = new PodStatus();
        var containerStatus = new ContainerStatus();
        containerStatus.setName(CONTAINER_NAME);
        containerStatus.setRestartCount(0);

        var state = new ContainerState();
        if (ready) {
            state.setRunning(new ContainerStateRunning());
        }
        containerStatus.setState(state);

        podStatus.setContainerStatuses(List.of(containerStatus));
        pod.setStatus(podStatus);

        return pod;
    }

    private Pod createPodWithTermination(String name, int restartCount, String reason, int exitCode, int signal) {
        var pod = createPod(name, true);
        var containerStatus = pod.getStatus().getContainerStatuses().getFirst();
        containerStatus.setRestartCount(restartCount);

        var terminated = new ContainerStateTerminated();
        terminated.setReason(reason);
        terminated.setExitCode(exitCode);
        terminated.setSignal(signal);
        terminated.setFinishedAt(Instant.now().toString());

        var lastState = new ContainerState();
        lastState.setTerminated(terminated);
        containerStatus.setLastState(lastState);

        return pod;
    }

    private Pod createPodWithMultipleTerminations(String name) {
        var pod = new Pod();
        var metadata = new ObjectMeta();
        metadata.setName(name);
        metadata.setCreationTimestamp(Instant.now().toString());
        pod.setMetadata(metadata);

        var podStatus = new PodStatus();

        // First container with earlier termination
        var cs1 = new ContainerStatus();
        cs1.setName("container-1");
        cs1.setRestartCount(1);
        var terminated1 = new ContainerStateTerminated();
        terminated1.setReason("EarlierReason");
        terminated1.setExitCode(1);
        terminated1.setFinishedAt(Instant.parse("2025-01-01T00:00:00Z").toString());
        var lastState1 = new ContainerState();
        lastState1.setTerminated(terminated1);
        cs1.setLastState(lastState1);
        cs1.setState(new ContainerState());

        // Second container with later termination
        var cs2 = new ContainerStatus();
        cs2.setName("container-2");
        cs2.setRestartCount(2);
        var terminated2 = new ContainerStateTerminated();
        terminated2.setReason("LaterReason");
        terminated2.setExitCode(2);
        terminated2.setFinishedAt(Instant.parse("2025-06-01T00:00:00Z").toString());
        var lastState2 = new ContainerState();
        lastState2.setTerminated(terminated2);
        cs2.setLastState(lastState2);
        cs2.setState(new ContainerState());

        podStatus.setContainerStatuses(List.of(cs1, cs2));
        pod.setStatus(podStatus);

        return pod;
    }
}
