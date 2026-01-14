package com.epam.aidial.deployment.manager.kubernetes.nim;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.AbstractK8sResourceClient;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.nvidia.apps.v1alpha1.NIMService;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LogExecution
public class K8sNimClient
        extends AbstractK8sResourceClient<NIMService, KubernetesResourceList<NIMService>> {

    private static final String NIM_SERVICE = "app.kubernetes.io/name";

    private final NimClient nimClient;

    public K8sNimClient(NimClient nimClient, K8sClient k8sClient) {
        super(k8sClient);
        this.nimClient = nimClient;
    }

    @Override
    protected MixedOperation<NIMService, KubernetesResourceList<NIMService>, Resource<NIMService>> getClient() {
        return nimClient.services();
    }

    @Override
    protected String getLabelKey() {
        return NIM_SERVICE;
    }

    @Override
    protected String getResourceName() {
        return "NIM Service";
    }

}
