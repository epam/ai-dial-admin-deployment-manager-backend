package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.mapping.ListMapper;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import com.epam.aidial.deployment.manager.utils.mapping.NimMappers;
import com.google.cloud.tools.jib.api.ImageReference;
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.NIMServiceSpec;
import com.nvidia.apps.v1alpha1.nimservicespec.Env;
import com.nvidia.apps.v1alpha1.nimservicespec.Expose;
import com.nvidia.apps.v1alpha1.nimservicespec.env.ValueFrom;
import com.nvidia.apps.v1alpha1.nimservicespec.env.valuefrom.SecretKeyRef;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Router;
import io.fabric8.kubernetes.api.model.IntOrString;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@LogExecution
public class NimManifestGenerator extends DeployableManifestGenerator {

    private static final String NIM_SERVED_MODEL_NAME_ENV = "NIM_SERVED_MODEL_NAME";

    private final NimProbeConverter nimProbeConverter;
    private final ProgressDeadlineCalculator progressDeadlineCalculator;

    public NimManifestGenerator(AppProperties appconfig,
                                NimProbeConverter nimProbeConverter,
                                ProgressDeadlineCalculator progressDeadlineCalculator) {
        super(appconfig);
        this.nimProbeConverter = nimProbeConverter;
        this.progressDeadlineCalculator = progressDeadlineCalculator;
    }

    @SneakyThrows
    public NIMService serviceConfig(
            String name,
            String serviceName,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            Resources resources,
            String imageName,
            int containerPort,
            @Nullable Integer containerGrpcPort,
            @Nullable Scaling scaling,
            @Nullable ProbeProperties probeProperties,
            int startupTimeoutSec,
            @Nullable List<String> command,
            @Nullable List<String> args
    ) {
        var config = createBaseManifestChain(
                appConfig::cloneNimServiceConfig,
                chain -> chain.get(NimMappers.SERVICE_METADATA_FIELD),
                serviceName
        );

        var specChain = config.get(NimMappers.SERVICE_SPEC_FIELD);
        configureImage(specChain, imageName);

        var envListMapper = specChain.getList(NimMappers.SERVICE_SPEC_ENVS_FIELD, NimMappers.ENV_VAR_NAME);
        applySimpleEnvs(envListMapper, envs, Env::setValue);
        applySensitiveEnvs(envListMapper, sensitiveEnv, Env::setValueFrom, this::buildNimSecretRef);
        setServedModelNameIfNotSet(name, envs, sensitiveEnv, envListMapper);

        var resourceLimitsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_LIMITS_FIELD);
        var resourceRequestsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_REQUESTS_FIELD);

        applyResourceMap(resourceLimitsChain.data(), resources.getLimits(), IntOrString::new);
        applyResourceMap(resourceRequestsChain.data(), resources.getRequests(), IntOrString::new);

        var exposeChain = specChain.get(NimMappers.SERVICE_SPEC_EXPOSE_FIELD);
        applyExposeService(exposeChain, containerPort, containerGrpcPort);
        exposeChain.data().setRouter(new Router());

        if (command != null) {
            specChain.data().setCommand(command);
        }
        if (args != null) {
            specChain.data().setArgs(args);
        }

        applyStartupProbe(name, specChain, probeProperties);
        applyProgressDeadline(probeProperties, startupTimeoutSec, config);
        applyScaling(name, scaling, config);

        return config.data();
    }

    private void applyExposeService(MappingChain<Expose> exposeChain, int httpPort, @Nullable Integer containerGrpcPort) {
        var serviceChain = exposeChain.get(NimMappers.EXPOSE_SERVICE_FIELD);
        var service = serviceChain.data();
        service.setPort(httpPort);
        if (containerGrpcPort != null) {
            service.setGrpcPort(containerGrpcPort);
        }
    }

    private void applyScaling(String name, @Nullable Scaling scaling, MappingChain<NIMService> config) {
        log.debug("Applying scaling for NIM deployment '{}': {}", name, scaling);
        if (scaling == null) {
            return;
        }

        var annotations = config.get(NimMappers.SERVICE_METADATA_FIELD)
                .get(NimMappers.METADATA_ANNOTATIONS_FIELD).data();
        applyScalingAnnotations(name, scaling, annotations);
    }

    private void applyStartupProbe(String name,
                                   MappingChain<NIMServiceSpec> specChain,
                                   @Nullable ProbeProperties deploymentProbeProperties) {
        var probe = nimProbeConverter.toNimStartupProbe(deploymentProbeProperties);
        if (probe != null) {
            log.debug("Applying startup probe for NIM deployment '{}': {}", name, probe);
            specChain.data().setStartupProbe(probe);
        }
    }

    private void applyProgressDeadline(@Nullable ProbeProperties probeProperties,
                                       int startupTimeoutSec,
                                       MappingChain<NIMService> config) {
        var progressDeadline = progressDeadlineCalculator.compute(probeProperties, startupTimeoutSec);
        var annotations = config.get(NimMappers.SERVICE_METADATA_FIELD)
                .get(NimMappers.METADATA_ANNOTATIONS_FIELD).data();
        annotations.put(KnativeAnnotations.PROGRESS_DEADLINE, progressDeadline);
    }

    @SneakyThrows
    private void configureImage(MappingChain<NIMServiceSpec> specChain, String imageName) {
        var serviceImage = specChain.get(NimMappers.SERVICE_SPEC_IMAGE_FIELD).data();
        var imageReference = ImageReference.parse(imageName);
        serviceImage.setRepository(imageReference.getRegistry() + "/" + imageReference.getRepository());
        serviceImage.setTag(imageReference.getTag().orElse("latest"));
    }

    private void setServedModelNameIfNotSet(String deploymentName,
                                            List<SimpleEnvVar> simpleEnvs,
                                            List<SensitiveEnvVar> sensitiveEnvs,
                                            ListMapper<Env> envListMapper) {
        boolean alreadySet = simpleEnvs.stream().anyMatch(e -> NIM_SERVED_MODEL_NAME_ENV.equals(e.getName()))
                || sensitiveEnvs.stream().anyMatch(e -> NIM_SERVED_MODEL_NAME_ENV.equals(e.getName()));
        if (alreadySet) {
            log.debug("Environment variable {} is already set for NIM deployment '{}', skipping.",
                    NIM_SERVED_MODEL_NAME_ENV, deploymentName);
            return;
        }
        envListMapper.get(NIM_SERVED_MODEL_NAME_ENV).data().setValue(deploymentName);
    }

    private ValueFrom buildNimSecretRef(SensitiveEnvVar env) {
        var secretKeyRef = new SecretKeyRef();
        secretKeyRef.setKey(env.getK8sSecretKey());
        secretKeyRef.setName(env.getK8sSecretName());
        var valueFrom = new ValueFrom();
        valueFrom.setSecretKeyRef(secretKeyRef);
        return valueFrom;
    }

}
