package com.epam.aidial.deployment.manager.configuration;

import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Ingress;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.kserve.serving.v1beta1.InferenceService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private static final String GPU_LIMIT_KEY = "nvidia.com/gpu";

    private Job builderJobConfig;
    private Container initBuilderContainerConfig;
    private Container builderRootContainerConfig;
    private Container builderRootlessContainerConfig;
    private Container pushContainerConfig;
    private Secret builderSecretConfig;

    private Job analyzerJobConfig;
    private Container analyzerContainerConfig;

    private Job copyImageJobConfig;
    private Container copyImageContainerConfig;

    private Service knativeServiceConfig;
    private Container knativeServiceContainerConfig;

    private NIMService nimServiceConfig;
    private Ingress nimServiceExposeIngressConfig;

    private InferenceService inferenceServiceConfig;
    private Container inferenceServiceContainerConfig;

    private String gitCloneImage;

    public Container getKnativeServiceContainerConfig() {
        // Removing GPU_LIMIT_KEY because Spring sometimes has issues with spilling configs
        if (knativeServiceContainerConfig != null && knativeServiceContainerConfig.getResources() != null) {
            var limits = knativeServiceContainerConfig.getResources().getLimits();
            if (limits != null) {
                limits.remove(GPU_LIMIT_KEY);
            }
            var requests = knativeServiceContainerConfig.getResources().getRequests();
            if (requests != null) {
                requests.remove(GPU_LIMIT_KEY);
            }
        }
        return knativeServiceContainerConfig;
    }

    public Job cloneBuilderJobConfig() {
        return new JobBuilder(builderJobConfig).build();
    }

    public Container cloneBuilderRootContainerConfig() {
        return new ContainerBuilder(builderRootContainerConfig).build();
    }

    public Container cloneBuilderRootlessContainerConfig() {
        return new ContainerBuilder(builderRootlessContainerConfig).build();
    }

    public Container clonePushContainerConfig() {
        return new ContainerBuilder(pushContainerConfig).build();
    }

    public Container cloneInitBuilderContainerConfig() {
        return new ContainerBuilder(initBuilderContainerConfig).build();
    }

    public Secret cloneBuilderSecretConfig() {
        return new SecretBuilder(builderSecretConfig).build();
    }

    public Job cloneAnalyzerJobConfig() {
        return new JobBuilder(analyzerJobConfig).build();
    }

    public Container cloneAnalyzerContainerConfig() {
        return new ContainerBuilder(analyzerContainerConfig).build();
    }

    public Job cloneCopyImageJobConfig() {
        return new JobBuilder(copyImageJobConfig).build();
    }

    public Container cloneCopyImageContainerConfig() {
        return new ContainerBuilder(copyImageContainerConfig).build();
    }

    public Service cloneKnativeServiceConfig() {
        return new ServiceBuilder(knativeServiceConfig).build();
    }

    public NIMService cloneNimServiceConfig() {
        return Serialization.clone(nimServiceConfig);
    }

    public InferenceService cloneInferenceServiceConfig() {
        return Serialization.clone(inferenceServiceConfig);
    }

    public Container cloneKnativeServiceContainer() {
        return new ContainerBuilder(getKnativeServiceContainerConfig()).build();
    }

}
