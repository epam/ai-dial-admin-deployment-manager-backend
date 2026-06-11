package com.epam.aidial.deployment.manager.service.deployment;

import com.epam.aidial.deployment.manager.model.EnvVar;
import com.epam.aidial.deployment.manager.model.PodInfo;
import com.epam.aidial.deployment.manager.model.ReconcileConfig;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.Deployment;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.util.List;

public interface DeploymentManager<S> {

    List<Class<? extends Deployment>> getSupportedDeploymentClasses();

    Deployment deploy(String id);

    boolean reconcile(String id, boolean ignorePendingOnServiceNotFound);

    boolean reconcile(ReconcileConfig<S> config);

    void stopOnServiceNotFound(String id);

    Deployment undeploy(String id);

    Deployment rollingUpdate(String id);

    void updateCiliumNetworkPolicy(String id);

    List<PodInfo> getActiveInstances(String id);

    List<PodInfo> getInstances(String id);

    /**
     * All instances of a deployment together with the Ready subset, resolved from a single pod
     * listing. A caller that needs both (e.g. replica counts plus a Ready pod to read from) would
     * otherwise pay two pod-list round-trips by calling {@link #getInstances(String)} and
     * {@link #getActiveInstances(String)} separately; this folds them into one. {@code ready}
     * carries the same readiness semantics as {@link #getActiveInstances(String)}.
     */
    PodInstances getInstancesWithReadiness(String id);

    /** All instances of a deployment plus the Ready subset, from one pod listing. */
    record PodInstances(List<PodInfo> all, List<PodInfo> ready) {
    }

    String getNamespace();

    int getDefaultContainerPort();

    NonNamespaceOperation<Event, EventList, Resource<Event>> getAllEventsBase();

    /**
     * Resolves a container resource for log streaming and performs a pre-flight validation:
     * - when {@code previous == false}: container must be in {@code Running} state
     * - when {@code previous == true}: container must have a previous terminated instance available
     *
     * <p>This avoids opening a log stream that immediately yields a Kubernetes {@code Status} error
     * (e.g. when the container is still {@code PodInitializing}).</p>
     */
    ContainerResource getContainerResourceForLogs(String id, String podName, boolean previous);

    List<SensitiveEnvVar> provisionSecrets(String deploymentId, EnvPartition envPartition);

    void cleanupSecrets(String deploymentId, List<EnvVar> currentEnvs);

    Deployment resolveSecrets(Deployment deployment);
}