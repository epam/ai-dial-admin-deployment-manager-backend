package com.epam.aidial.deployment.manager.utils.mapping;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class Mappers {
    public static final NamedItemMapper<Container> CONTAINER_NAME = new NamedItemMapper<>(
            Container::new,
            Container::getName,
            Container::setName);

    public static final NamedItemMapper<EnvVar> ENV_VAR_NAME = new NamedItemMapper<>(
            EnvVar::new,
            EnvVar::getName,
            EnvVar::setName);

    public static final NamedItemMapper<Volume> VOLUME_NAME = new NamedItemMapper<>(
            Volume::new,
            Volume::getName,
            Volume::setName);

    public static final NamedItemMapper<VolumeMount> VOLUME_MOUNT_PATH = new NamedItemMapper<>(
            VolumeMount::new,
            VolumeMount::getMountPath,
            VolumeMount::setMountPath);

    public static final FieldMapper<Secret, ObjectMeta> SECRET_METADATA_FIELD = new FieldMapper<>(
            ObjectMeta::new,
            Secret::getMetadata,
            Secret::setMetadata);

    public static final FieldMapper<Job, ObjectMeta> JOB_METADATA_FIELD = new FieldMapper<>(
            ObjectMeta::new,
            Job::getMetadata,
            Job::setMetadata);

    public static final FieldMapper<Job, JobSpec> JOB_SPEC_FIELD = new FieldMapper<>(
            JobSpec::new,
            Job::getSpec,
            Job::setSpec);

    public static final FieldMapper<JobSpec, PodTemplateSpec> JOB_TEMPLATE_FIELD = new FieldMapper<>(
            PodTemplateSpec::new,
            JobSpec::getTemplate,
            JobSpec::setTemplate);

    public static final FieldMapper<PodTemplateSpec, PodSpec> JOB_TEMPLATE_SPEC_FIELD = new FieldMapper<>(
            PodSpec::new,
            PodTemplateSpec::getSpec,
            PodTemplateSpec::setSpec);

    public static final FieldMapper<PodSpec, List<Container>> POD_CONTAINERS_FIELD = new FieldMapper<>(
            ArrayList::new,
            PodSpec::getContainers,
            PodSpec::setContainers);

    public static final FieldMapper<PodSpec, List<Container>> POD_INIT_CONTAINERS_FIELD = new FieldMapper<>(
            ArrayList::new,
            PodSpec::getInitContainers,
            PodSpec::setInitContainers);

    public static final FieldMapper<PodSpec, List<Volume>> POD_VOLUMES_FIELD = new FieldMapper<>(
            ArrayList::new,
            PodSpec::getVolumes,
            PodSpec::setVolumes);

    public static final FieldMapper<Container, List<EnvVar>> CONTAINER_ENV_FIELD = new FieldMapper<>(
            ArrayList::new,
            Container::getEnv,
            Container::setEnv);

    public static final FieldMapper<Container, List<String>> CONTAINER_ARGS_FIELD = new FieldMapper<>(
            ArrayList::new,
            Container::getArgs,
            Container::setArgs);

    public static final FieldMapper<Container, List<String>> CONTAINER_COMMAND_FIELD = new FieldMapper<>(
            ArrayList::new,
            Container::getCommand,
            Container::setCommand);

    public static final FieldMapper<Container, List<VolumeMount>> CONTAINER_VOLUME_MOUNTS_FIELD = new FieldMapper<>(
            ArrayList::new,
            Container::getVolumeMounts,
            Container::setVolumeMounts);

    public static final FieldMapper<Container, ResourceRequirements> CONTAINER_RESOURCES_FIELD = new FieldMapper<>(
            ResourceRequirements::new,
            Container::getResources,
            Container::setResources);

    public static final FieldMapper<Container, List<ContainerPort>> CONTAINER_PORTS_FIELD = new FieldMapper<>(
            ArrayList::new,
            Container::getPorts,
            Container::setPorts);

    public static final FieldMapper<ResourceRequirements, Map<String, Quantity>> RESOURCES_REQUESTS_FIELD = new FieldMapper<>(
            HashMap::new,
            ResourceRequirements::getRequests,
            ResourceRequirements::setRequests);

    public static final FieldMapper<ResourceRequirements, Map<String, Quantity>> RESOURCES_LIMITS_FIELD = new FieldMapper<>(
            HashMap::new,
            ResourceRequirements::getLimits,
            ResourceRequirements::setLimits);

}