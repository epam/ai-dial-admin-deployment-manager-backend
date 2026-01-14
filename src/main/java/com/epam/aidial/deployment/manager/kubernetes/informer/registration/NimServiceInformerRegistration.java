package com.epam.aidial.deployment.manager.kubernetes.informer.registration;

import com.epam.aidial.deployment.manager.configuration.NimDeployProperties;
import com.epam.aidial.deployment.manager.kubernetes.informer.handler.NimServiceEventHandler;
import com.epam.aidial.deployment.manager.kubernetes.nim.K8sNimClient;
import com.nvidia.apps.v1alpha1.NIMService;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registration for NIM Service informer.
 * Creates, starts, and stops the informer for NIM Service resources.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.nim.enabled", havingValue = "true")
public class NimServiceInformerRegistration extends AbstractInformerRegistration<NIMService> {

    private final K8sNimClient k8sNimClient;
    private final NimDeployProperties properties;

    public NimServiceInformerRegistration(
            K8sNimClient k8sNimClient,
            NimServiceEventHandler eventHandler,
            NimDeployProperties nimDeployProperties) {
        super(eventHandler);
        this.k8sNimClient = k8sNimClient;
        this.properties = nimDeployProperties;
    }

    @Override
    public String getResourceType() {
        return "NIMService";
    }

    @Override
    protected SharedIndexInformer<NIMService> createInformer() {
        return k8sNimClient.createInformer(properties.getNamespace(), properties.getInformerResyncInterval());
    }
}

