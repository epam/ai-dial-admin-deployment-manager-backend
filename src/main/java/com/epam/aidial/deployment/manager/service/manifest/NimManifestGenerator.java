package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import com.epam.aidial.deployment.manager.utils.mapping.NimMappers;
import com.google.cloud.tools.jib.api.ImageReference;
import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.NIMServiceSpec;
import com.nvidia.apps.v1alpha1.nimservicespec.Env;
import com.nvidia.apps.v1alpha1.nimservicespec.env.ValueFrom;
import com.nvidia.apps.v1alpha1.nimservicespec.env.valuefrom.SecretKeyRef;
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

    private final NimProbeConverter nimProbeConverter;

    public NimManifestGenerator(AppProperties appconfig,
                                NimProbeConverter nimProbeConverter) {
        super(appconfig);
        this.nimProbeConverter = nimProbeConverter;
    }

    @SneakyThrows
    public NIMService serviceConfig(
            String name,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            Resources resources,
            String imageName,
            Integer containerPort,
            Integer containerGrpcPort,
            @Nullable ProbeProperties probeProperties
    ) {
        var config = createBaseManifestChain(
                appConfig::cloneNimServiceConfig,
                chain -> chain.get(NimMappers.SERVICE_METADATA_FIELD),
                K8sNamingUtils.generateMcpPrefixedName(name)
        );

        var specChain = config.get(NimMappers.SERVICE_SPEC_FIELD);
        configureImage(specChain, imageName);

        var envListMapper = specChain.getList(NimMappers.SERVICE_SPEC_ENVS_FIELD, NimMappers.ENV_VAR_NAME);
        applySimpleEnvs(envListMapper, envs, Env::setValue);
        applySensitiveEnvs(envListMapper, sensitiveEnv, Env::setValueFrom, this::buildNimSecretRef);

        var resourceLimitsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_LIMITS_FIELD);
        var resourceRequestsChain = specChain.get(NimMappers.SERVICE_SPEC_RESOURCES_FIELD)
                .get(NimMappers.RESOURCES_REQUESTS_FIELD);

        applyResourceMap(resourceLimitsChain.data(), resources.getLimits(), IntOrString::new);
        applyResourceMap(resourceRequestsChain.data(), resources.getRequests(), IntOrString::new);

        var exposeChain = specChain.get(NimMappers.SERVICE_SPEC_EXPOSE_FIELD);
        var serviceChain = exposeChain.get(NimMappers.EXPOSE_SERVICE_FIELD);
        var service = serviceChain.data();

        if (containerPort != null) {
            service.setPort(containerPort);
        }
        if (containerGrpcPort != null) {
            service.setGrpcPort(containerGrpcPort);
        }

        applyStartupProbe(name, specChain, probeProperties);

        return config.data();
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

    @SneakyThrows
    private void configureImage(MappingChain<NIMServiceSpec> specChain, String imageName) {
        var serviceImage = specChain.get(NimMappers.SERVICE_SPEC_IMAGE_FIELD).data();
        var imageReference = ImageReference.parse(imageName);
        serviceImage.setRepository(imageReference.getRegistry() + "/" + imageReference.getRepository());
        serviceImage.setTag(imageReference.getTag().orElse("latest"));
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
