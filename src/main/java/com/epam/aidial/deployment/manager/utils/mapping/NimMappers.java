package com.epam.aidial.deployment.manager.utils.mapping;

import com.nvidia.apps.v1alpha1.NIMService;
import com.nvidia.apps.v1alpha1.NIMServiceSpec;
import com.nvidia.apps.v1alpha1.nimservicespec.Env;
import com.nvidia.apps.v1alpha1.nimservicespec.Expose;
import com.nvidia.apps.v1alpha1.nimservicespec.Image;
import com.nvidia.apps.v1alpha1.nimservicespec.Resources;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Ingress;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.Service;
import com.nvidia.apps.v1alpha1.nimservicespec.expose.ingress.Spec;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class NimMappers {

    public static final NamedItemMapper<Env> ENV_VAR_NAME = new NamedItemMapper<>(
            Env::new,
            Env::getName,
            Env::setName);

    public static final FieldMapper<NIMService, ObjectMeta> SERVICE_METADATA_FIELD = new FieldMapper<>(
            ObjectMeta::new,
            NIMService::getMetadata,
            NIMService::setMetadata);

    public static final FieldMapper<ObjectMeta, Map<String, String>> METADATA_ANNOTATIONS_FIELD = new FieldMapper<>(
            HashMap::new,
            ObjectMeta::getAnnotations,
            ObjectMeta::setAnnotations);

    public static final FieldMapper<NIMService, NIMServiceSpec> SERVICE_SPEC_FIELD = new FieldMapper<>(
            NIMServiceSpec::new,
            NIMService::getSpec,
            NIMService::setSpec);

    public static final FieldMapper<NIMServiceSpec, Image> SERVICE_SPEC_IMAGE_FIELD = new FieldMapper<>(
            Image::new,
            NIMServiceSpec::getImage,
            NIMServiceSpec::setImage);

    public static final FieldMapper<NIMServiceSpec, List<Env>> SERVICE_SPEC_ENVS_FIELD = new FieldMapper<>(
            ArrayList::new,
            NIMServiceSpec::getEnv,
            NIMServiceSpec::setEnv);

    public static final FieldMapper<NIMServiceSpec, Resources> SERVICE_SPEC_RESOURCES_FIELD = new FieldMapper<>(
            Resources::new,
            NIMServiceSpec::getResources,
            NIMServiceSpec::setResources);

    public static final FieldMapper<Resources, Map<String, IntOrString>> RESOURCES_REQUESTS_FIELD = new FieldMapper<>(
            HashMap::new,
            Resources::getRequests,
            Resources::setRequests);

    public static final FieldMapper<Resources, Map<String, IntOrString>> RESOURCES_LIMITS_FIELD = new FieldMapper<>(
            HashMap::new,
            Resources::getLimits,
            Resources::setLimits);

    public static final FieldMapper<NIMServiceSpec, Expose> SERVICE_SPEC_EXPOSE_FIELD = new FieldMapper<>(
            Expose::new,
            NIMServiceSpec::getExpose,
            NIMServiceSpec::setExpose);

    public static final FieldMapper<Expose, Service> EXPOSE_SERVICE_FIELD = new FieldMapper<>(
            Service::new,
            Expose::getService,
            Expose::setService);

    public static final FieldMapper<Ingress, Spec> INGRESS_SPEC_FIELD = new FieldMapper<>(
            Spec::new,
            Ingress::getSpec,
            Ingress::setSpec);

}