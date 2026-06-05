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