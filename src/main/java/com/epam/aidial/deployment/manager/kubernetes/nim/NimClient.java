package com.epam.aidial.deployment.manager.kubernetes.nim;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.nvidia.apps.v1alpha1.NIMService;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A dedicated client for interacting with NIMService Custom Resources within a Kubernetes cluster.
 *
 * <p>
 * This class acts as a facade over the generic Fabric8 KubernetesClient, providing a
 * type-safe entry point for NIMService operations.
 */
@Component
@LogExecution
@RequiredArgsConstructor
public class NimClient {

    private final KubernetesClient kubernetesClient;

    /**
     * Provides a client for operating on NIMService resources. This is the entry point
     * for all CRUD (Create, Read, Update, Delete) and other operations.
     *
     * <p>
     * The returned {@link MixedOperation} can be used to target all namespaces or a
     * specific one using {@code .inNamespace("...")}.
     *
     * @return a {@link MixedOperation} for NIMService resources.
     */
    public MixedOperation<NIMService, KubernetesResourceList<NIMService>, Resource<NIMService>> services() {
        return kubernetesClient.resources(NIMService.class);
    }

}