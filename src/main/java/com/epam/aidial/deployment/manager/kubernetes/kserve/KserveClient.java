package com.epam.aidial.deployment.manager.kubernetes.kserve;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.kserve.serving.v1beta1.InferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@LogExecution
@RequiredArgsConstructor
public class KserveClient {

    private final KubernetesClient kubernetesClient;

    public MixedOperation<InferenceService, KubernetesResourceList<InferenceService>, Resource<InferenceService>> services() {
        return kubernetesClient.resources(InferenceService.class);
    }

}