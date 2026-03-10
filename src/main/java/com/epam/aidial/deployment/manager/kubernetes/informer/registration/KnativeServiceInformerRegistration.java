package com.epam.aidial.deployment.manager.kubernetes.informer.registration;

import com.epam.aidial.deployment.manager.configuration.KnativeDeployProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.informer.handler.KnativeServiceEventHandler;
import com.epam.aidial.deployment.manager.kubernetes.knative.K8sKnativeClient;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registration for Knative Service informer.
 * Creates, starts, and stops the informer for Knative Service resources.
 */
@Slf4j
@Component
@LogExecution
@ConditionalOnProperty(name = "app.knative.enabled", havingValue = "true")
public class KnativeServiceInformerRegistration extends AbstractInformerRegistration<Service> {

    private final K8sKnativeClient k8sKnativeClient;
    private final KnativeDeployProperties properties;

    public KnativeServiceInformerRegistration(
            K8sKnativeClient k8sKnativeClient,
            KnativeServiceEventHandler eventHandler,
            KnativeDeployProperties knativeDeployProperties) {
        super(eventHandler);
        this.k8sKnativeClient = k8sKnativeClient;
        this.properties = knativeDeployProperties;
    }

    @Override
    public String getResourceType() {
        return "KnativeService";
    }

    @Override
    protected SharedIndexInformer<Service> createInformer() {
        return k8sKnativeClient.createInformer(properties.getNamespace(), properties.getInformerResyncInterval());
    }

}

