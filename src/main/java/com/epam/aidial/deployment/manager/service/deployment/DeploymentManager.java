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
import java.util.UUID;

public interface DeploymentManager<S> {

    Deployment deploy(UUID id);

    boolean reconcile(UUID id, boolean ignorePendingOnServiceNotFound);

    boolean reconcile(ReconcileConfig<S> config);

    Deployment undeploy(UUID id);

    void rollingUpdate(UUID id);

    List<PodInfo> getActiveInstances(UUID id);

    List<PodInfo> getInstances(UUID id);

    NonNamespaceOperation<Event, EventList, Resource<Event>> getAllEventsBase();

    ContainerResource getContainerResource(UUID id, String podName);

    List<SensitiveEnvVar> provisionSecrets(UUID deploymentId, EnvPartition envPartition);

    void cleanupSecrets(UUID deploymentId, List<EnvVar> currentEnvs);

    Deployment resolveSecrets(Deployment deployment);
}