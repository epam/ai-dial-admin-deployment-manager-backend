package com.epam.aidial.deployment.manager.utils.mapping;

import io.fabric8.knative.serving.v1.RevisionSpec;
import io.fabric8.knative.serving.v1.RevisionTemplateSpec;
import io.fabric8.knative.serving.v1.Service;
import io.fabric8.knative.serving.v1.ServiceSpec;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Volume;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class KnativeMappers {

    public static final FieldMapper<Service, ObjectMeta> SERVICE_METADATA_FIELD = new FieldMapper<>(
            ObjectMeta::new,
            Service::getMetadata,
            Service::setMetadata);

    public static final FieldMapper<Service, ServiceSpec> SERVICE_SPEC_FIELD = new FieldMapper<>(
            ServiceSpec::new,
            Service::getSpec,
            Service::setSpec);

    public static final FieldMapper<ServiceSpec, RevisionTemplateSpec> SERVICE_TEMPLATE_FIELD = new FieldMapper<>(
            RevisionTemplateSpec::new,
            ServiceSpec::getTemplate,
            ServiceSpec::setTemplate);

    public static final FieldMapper<RevisionTemplateSpec, ObjectMeta> SERVICE_TEMPLATE_METADATA_FIELD = new FieldMapper<>(
            ObjectMeta::new,
            RevisionTemplateSpec::getMetadata,
            RevisionTemplateSpec::setMetadata);

    public static final FieldMapper<RevisionTemplateSpec, RevisionSpec> SERVICE_TEMPLATE_SPEC_FIELD = new FieldMapper<>(
            RevisionSpec::new,
            RevisionTemplateSpec::getSpec,
            RevisionTemplateSpec::setSpec);

    public static final FieldMapper<RevisionSpec, List<Container>> TEMPLATE_CONTAINERS_FIELD = new FieldMapper<>(
            ArrayList::new,
            RevisionSpec::getContainers,
            RevisionSpec::setContainers);

    public static final FieldMapper<RevisionSpec, List<Volume>> REVISION_SPEC_VOLUMES_FIELD = new FieldMapper<>(
            ArrayList::new,
            RevisionSpec::getVolumes,
            RevisionSpec::setVolumes
    );
}