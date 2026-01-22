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

    boolean reconcile(ReconcileConfig<S> config);

    Deployment undeploy(String id);

    void rollingUpdate(String id);

    List<PodInfo> getActiveInstances(String id);

    List<PodInfo> getInstances(String id);

    NonNamespaceOperation<Event, EventList, Resource<Event>> getAllEventsBase();

    ContainerResource getContainerResource(String id, String podName);

    List<SensitiveEnvVar> provisionSecrets(String deploymentId, EnvPartition envPartition);

    void cleanupSecrets(String deploymentId, List<EnvVar> currentEnvs);

    Deployment resolveSecrets(Deployment deployment);
}