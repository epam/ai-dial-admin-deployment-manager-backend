package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SensitiveFileEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
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
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@LogExecution
public class KnativeManifestGenerator extends DeployableManifestGenerator {

    @Value("${app.secrets-volume-mount-path}")
    private String secretsVolumeMountPath;

    private final ProbeConverter probeConverter;
    private final ProgressDeadlineCalculator progressDeadlineCalculator;

    public KnativeManifestGenerator(AppProperties appconfig,
                                    ProbeConverter probeConverter,
                                    ProgressDeadlineCalculator progressDeadlineCalculator) {
        super(appconfig);
        this.probeConverter = probeConverter;
        this.progressDeadlineCalculator = progressDeadlineCalculator;
    }

    public Service serviceConfig(
            String name,
            String serviceName,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            List<SensitiveFileEnvVar> sensitiveFileEnvs,
            String imageName,
            @Nullable Scaling scaling,
            Resources resources,
            @Nullable Integer containerPort,
            @Nullable ProbeProperties probeProperties,
            @Nullable List<String> command,
            @Nullable List<String> args,
            PoolSchedulingPrimitives poolPrimitives
    ) {
        var config = createBaseManifestChain(
                appConfig::cloneKnativeServiceConfig,
                chain -> chain.get(KnativeMappers.SERVICE_METADATA_FIELD),
                serviceName
        );

        var template = config.get(KnativeMappers.SERVICE_SPEC_FIELD)
                .get(KnativeMappers.SERVICE_TEMPLATE_FIELD);

        var revisionSpecChain = template.get(KnativeMappers.SERVICE_TEMPLATE_SPEC_FIELD);
        applyScaling(name, scaling, template, revisionSpecChain);
        applyProgressDeadline(probeProperties, template);
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

        if (command != null) {
            containerChain.data().setCommand(command);
        }
        if (args != null) {
            containerChain.data().setArgs(args);
        }

        applyStartupProbe(name, containerChain, probeProperties);

        applyPoolPrimitives(revisionSpecChain, poolPrimitives);

        return config.data();
    }

    private void applyPoolPrimitives(MappingChain<RevisionSpec> revisionSpecChain, PoolSchedulingPrimitives primitives) {
        if (primitives == null || primitives.isEmpty()) {
            return;
        }
        if (MapUtils.isNotEmpty(primitives.nodeSelector())) {
            revisionSpecChain.data().setNodeSelector(primitives.nodeSelector());
        }
        if (primitives.affinity() != null) {
            revisionSpecChain.data().setAffinity(primitives.affinity());
        }
        if (CollectionUtils.isNotEmpty(primitives.tolerations())) {
            var existing = revisionSpecChain.data().getTolerations();
            var merged = new ArrayList<Toleration>();
            if (existing != null) {
                merged.addAll(existing);
            }
            merged.addAll(primitives.tolerations());
            revisionSpecChain.data().setTolerations(merged);
        }
    }

    private void applyStartupProbe(String name,
                                   MappingChain<Container> containerChain,
                                   @Nullable ProbeProperties deploymentProbeProperties) {
        Probe probe = probeConverter.toProbe(deploymentProbeProperties);
        if (probe != null) {
            log.debug("Applying startup probe for Knative deployment '{}': {}", name, probe);
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

    private void applyScaling(String name,
                              @Nullable Scaling scaling,
                              MappingChain<RevisionTemplateSpec> template,
                              MappingChain<RevisionSpec> revisionSpecChain) {
        log.debug("Applying scaling for Knative deployment '{}': {}", name, scaling);
        if (scaling == null) {
            return;
        }

        var annotations = template.get(KnativeMappers.SERVICE_TEMPLATE_METADATA_FIELD)
                .get(KnativeMappers.METADATA_ANNOTATIONS_FIELD).data();
        applyScalingAnnotations(name, scaling, annotations);

        if (scaling.getStrategy() == null) {
            return;
        }

        if (scaling.getStrategy().getType() == ScalingStrategyType.ACTIVE_REQUESTS) {
            revisionSpecChain.data().setContainerConcurrency((long) scaling.getStrategy().getThreshold());
            log.trace("Applied strategy ACTIVE_REQUESTS: containerConcurrency={} for Knative deployment '{}'",
                    scaling.getStrategy().getThreshold(), name);
        }
    }

    private void applyProgressDeadline(@Nullable ProbeProperties probeProperties,
                                       MappingChain<RevisionTemplateSpec> template) {
        var progressDeadline = progressDeadlineCalculator.compute(probeProperties);
        if (progressDeadline != null) {
            var annotations = template.get(KnativeMappers.SERVICE_TEMPLATE_METADATA_FIELD)
                    .get(KnativeMappers.METADATA_ANNOTATIONS_FIELD).data();
            annotations.put(KnativeAnnotations.PROGRESS_DEADLINE, progressDeadline);
        }
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
