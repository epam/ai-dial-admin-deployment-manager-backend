package com.epam.aidial.deployment.manager.kubernetes.informer.registration;

import com.epam.aidial.deployment.manager.configuration.KserveDeployProperties;
import com.epam.aidial.deployment.manager.kubernetes.informer.handler.InferenceServiceEventHandler;
import com.epam.aidial.deployment.manager.kubernetes.kserve.K8sKserveClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.kserve.serving.v1beta1.InferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registration for Inference Service informer.
 * Creates, starts, and stops the informer for Inference Service resources.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kserve.enabled", havingValue = "true")
public class InferenceServiceInformerRegistration extends AbstractInformerRegistration<InferenceService> {

    private final K8sKserveClient k8sKserveClient;
    private final KserveDeployProperties properties;

    public InferenceServiceInformerRegistration(
            K8sKserveClient k8sKserveClient,
            InferenceServiceEventHandler eventHandler,
            KserveDeployProperties kserveDeployProperties) {
        super(eventHandler);
        this.k8sKserveClient = k8sKserveClient;
        this.properties = kserveDeployProperties;
    }

    @Override
    public String getResourceType() {
        return "InferenceService";
    }

    @Override
    protected SharedIndexInformer<InferenceService> createInformer() {
        return k8sKserveClient.createInformer(properties.getNamespace(), properties.getInformerResyncInterval());
    }
}

