package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.KnativeMappers;
import com.epam.aidial.deployment.manager.utils.mapping.ListMapper;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.knative.serving.v1.RevisionSpec;
import io.fabric8.knative.serving.v1.RevisionTemplateSpec;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
@LogExecution
public class KnativeManifestGenerator extends DeployableManifestGenerator {

    @Value("${app.secrets-volume-mount-path}")
    private String secretsVolumeMountPath;

    private final ProbeConverter probeConverter;

    public KnativeManifestGenerator(AppProperties appconfig,
                                    ProbeConverter probeConverter) {
        super(appconfig);
        this.probeConverter = probeConverter;
    }

    public Service serviceConfig(
            String name,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            List<SensitiveFileEnvVar> sensitiveFileEnvs,
            String imageName,
            @Nullable Integer initScale,
            @Nullable Integer minScale,
            @Nullable Integer maxScale,
            Resources resources,
            @Nullable Integer containerPort,
            @Nullable ProbeProperties probeProperties
    ) {
        var config = createBaseManifestChain(
                appConfig::cloneKnativeServiceConfig,
                chain -> chain.get(KnativeMappers.SERVICE_METADATA_FIELD),
                K8sNamingUtils.generateMcpPrefixedName(name)
        );

        var template = config.get(KnativeMappers.SERVICE_SPEC_FIELD)
                .get(KnativeMappers.SERVICE_TEMPLATE_FIELD);

        configureAnnotations(template, initScale, minScale, maxScale);

        var revisionSpecChain = template.get(KnativeMappers.SERVICE_TEMPLATE_SPEC_FIELD);
        var containerChain = revisionSpecChain
                .getList(KnativeMappers.TEMPLATE_CONTAINERS_FIELD, Mappers.CONTAINER_NAME)
                .getOrDefault(appConfig.getKnativeServiceContainerConfig().getName(), appConfig::cloneKnativeServiceContainer);

        containerChain.data().setImage(imageName);

        var envListMapper = containerChain.getList(Mappers.CONTAINER_ENV_FIELD, Mappers.ENV_VAR_NAME);
        applySimpleEnvs(envListMapper, envs, EnvVar::setValue);
        applySensitiveEnvs(envListMapper, sensitiveEnv, EnvVar::setValueFrom, this::buildKnativeSecretRef);

        for (var envVar : sensitiveFileEnvs) {
            addSecretVolumesAndMountsAndApplySensitiveFileEnv(revisionSpecChain, containerChain, envVar, envListMapper);
        }

        var resourceLimitsChain = containerChain.get(Mappers.CONTAINER_RESOURCES_FIELD)
                .get(Mappers.RESOURCES_LIMITS_FIELD);
        var resourceRequestsChain = containerChain.get(Mappers.CONTAINER_RESOURCES_FIELD)
                .get(Mappers.RESOURCES_REQUESTS_FIELD);

        applyResourceMap(resourceLimitsChain.data(), resources.getLimits(), Quantity::new);
        applyResourceMap(resourceRequestsChain.data(), resources.getRequests(), Quantity::new);

        // Configure container port if specified
        if (containerPort != null) {
            var portsChain = containerChain.get(Mappers.CONTAINER_PORTS_FIELD);
            var portsList = portsChain.data();
            portsList.clear(); // Clear any existing ports

            var port = new ContainerPort();
            port.setContainerPort(containerPort);
            portsList.add(port);
        }

        applyStartupProbe(containerChain, probeProperties);

        return config.data();
    }

    private void applyStartupProbe(MappingChain<Container> containerChain,
                                   @Nullable ProbeProperties deploymentProbeProperties) {
        Probe probe = probeConverter.toProbe(deploymentProbeProperties);
        if (probe != null) {
            containerChain.data().setStartupProbe(probe);
        }
    }

    private void addSecretVolumesAndMountsAndApplySensitiveFileEnv(
            MappingChain<RevisionSpec> revisionSpecChain,
            MappingChain<Container> containerChain,
            SensitiveEnvVar envVar,
            ListMapper<EnvVar> envListMapper) {
        final String volumeName = envVar.getK8sSecretName();

        var volumesMapper = revisionSpecChain.getList(KnativeMappers.REVISION_SPEC_VOLUMES_FIELD, Mappers.VOLUME_NAME);
        var volumeMountsMapper = containerChain.getList(Mappers.CONTAINER_VOLUME_MOUNTS_FIELD, Mappers.VOLUME_MOUNT_PATH);

        volumesMapper.getOrDefault(volumeName, () -> {
            var volume = new Volume();
            volume.setName(volumeName);
            volume.setSecret(new SecretVolumeSourceBuilder()
                    .withSecretName(envVar.getK8sSecretName())
                    .build());
            return volume;
        });

        volumeMountsMapper.getOrDefault(secretsVolumeMountPath, () -> {
            var volumeMount = new VolumeMount();
            volumeMount.setName(volumeName);
            volumeMount.setMountPath(secretsVolumeMountPath);
            volumeMount.setReadOnly(true);
            return volumeMount;
        });

        //set sensitiveFile envvar value
        var envVarChain = envListMapper.get(envVar.getName());
        String filePathToVolume = "%s/%s".formatted(secretsVolumeMountPath, envVar.getK8sSecretKey());
        envVarChain.data().setValue(filePathToVolume);
    }

    private void configureAnnotations(
            MappingChain<RevisionTemplateSpec> template,
            @Nullable Integer initScale,
            @Nullable Integer minScale,
            @Nullable Integer maxScale
    ) {
        var templateMetadata = template.get(KnativeMappers.SERVICE_TEMPLATE_METADATA_FIELD).data();
        var annotations = (templateMetadata.getAnnotations() != null)
                ? templateMetadata.getAnnotations()
                : new HashMap<String, String>();

        if (initScale != null) {
            annotations.put("autoscaling.knative.dev/initial-scale", String.valueOf(initScale));
        }
        if (minScale != null) {
            annotations.put("autoscaling.knative.dev/min-scale", String.valueOf(minScale));
        }
        if (maxScale != null) {
            annotations.put("autoscaling.knative.dev/max-scale", String.valueOf(maxScale));
        }
        templateMetadata.setAnnotations(annotations);
    }

    private EnvVarSource buildKnativeSecretRef(SensitiveEnvVar env) {
        return new EnvVarSourceBuilder()
                .withNewSecretKeyRef()
                .withName(env.getK8sSecretName())
                .withKey(env.getK8sSecretKey())
                .endSecretKeyRef()
                .build();
    }
}
