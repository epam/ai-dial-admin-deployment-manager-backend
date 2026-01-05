package com.epam.aidial.deployment.manager.kubernetes.kserve;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.AbstractK8sResourceClient;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.kserve.serving.v1beta1.InferenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LogExecution
public class K8sKserveClient
        extends AbstractK8sResourceClient<InferenceService, KubernetesResourceList<InferenceService>> {

    private static final String KSERVE_SERVICE = "serving.kserve.io/inferenceservice";

    private final KserveClient kserveClient;

    public K8sKserveClient(KserveClient kserveClient, K8sClient k8sClient) {
        super(k8sClient);
        this.kserveClient = kserveClient;
    }

    @Override
    protected MixedOperation<InferenceService, KubernetesResourceList<InferenceService>, Resource<InferenceService>> getClient() {
        return kserveClient.services();
    }

    @Override
    protected String getLabelKey() {
        return KSERVE_SERVICE;
    }

    @Override
    protected String getResourceName() {
        return "Kserve Service";
    }
}
