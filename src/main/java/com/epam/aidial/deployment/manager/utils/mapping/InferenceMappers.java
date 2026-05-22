package com.epam.aidial.deployment.manager.utils.mapping;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.InferenceServiceSpec;
import io.kserve.serving.v1beta1.inferenceservicespec.Predictor;
import io.kserve.serving.v1beta1.inferenceservicespec.Transformer;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Model;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.Env;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.ModelFormat;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.Resources;
import io.kserve.serving.v1beta1.inferenceservicespec.transformer.Containers;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class InferenceMappers {

    public static final FieldMapper<InferenceService, ObjectMeta> SERVICE_METADATA_FIELD = new FieldMapper<>(
            ObjectMeta::new,
            InferenceService::getMetadata,
            InferenceService::setMetadata);

    public static final FieldMapper<ObjectMeta, Map<String, String>> METADATA_ANNOTATIONS_FIELD = new FieldMapper<>(
            HashMap::new,
            ObjectMeta::getAnnotations,
            ObjectMeta::setAnnotations);

    public static final FieldMapper<InferenceService, InferenceServiceSpec> SERVICE_SPEC_FIELD = new FieldMapper<>(
            InferenceServiceSpec::new,
            InferenceService::getSpec,
            InferenceService::setSpec);

    public static final FieldMapper<InferenceServiceSpec, Predictor> SERVICE_SPEC_PREDICTOR_FIELD = new FieldMapper<>(
            Predictor::new,
            InferenceServiceSpec::getPredictor,
            InferenceServiceSpec::setPredictor);

    public static final FieldMapper<Predictor, Model> PREDICTOR_MODEL_FIELD = new FieldMapper<>(
            Model::new,
            Predictor::getModel,
            Predictor::setModel);

    public static final NamedItemMapper<Env> ENV_VAR_NAME = new NamedItemMapper<>(
            Env::new,
            Env::getName,
            Env::setName);

    public static final FieldMapper<Model, ModelFormat> MODEL_FORMAT_FIELD = new FieldMapper<>(
            ModelFormat::new,
            Model::getModelFormat,
            Model::setModelFormat);

    public static final FieldMapper<Model, List<Env>> MODEL_ENV_FIELD = new FieldMapper<>(
            ArrayList::new,
            Model::getEnv,
            Model::setEnv);

    public static final FieldMapper<Model, List<String>> MODEL_ARGS_FIELD = new FieldMapper<>(
            ArrayList::new,
            Model::getArgs,
            Model::setArgs);

    public static final FieldMapper<Model, List<String>> MODEL_COMMAND_FIELD = new FieldMapper<>(
            ArrayList::new,
            Model::getCommand,
            Model::setCommand);

    public static final FieldMapper<Model, Resources> MODEL_RESOURCES_FIELD = new FieldMapper<>(
            Resources::new,
            Model::getResources,
            Model::setResources);

    public static final FieldMapper<Resources, Map<String, IntOrString>> RESOURCES_LIMITS_FIELD = new FieldMapper<>(
            HashMap::new,
            Resources::getLimits,
            Resources::setLimits);

    public static final FieldMapper<Resources, Map<String, IntOrString>> RESOURCES_REQUESTS_FIELD = new FieldMapper<>(
            HashMap::new,
            Resources::getRequests,
            Resources::setRequests);

    public static final FieldMapper<InferenceServiceSpec, Transformer> SERVICE_SPEC_TRANSFORMER_FIELD = new FieldMapper<>(
            Transformer::new,
            InferenceServiceSpec::getTransformer,
            InferenceServiceSpec::setTransformer);

    public static final FieldMapper<Transformer, List<Containers>> TRANSFORMER_CONTAINERS_FIELD = new FieldMapper<>(
            ArrayList::new,
            Transformer::getContainers,
            Transformer::setContainers);

    public static final FieldMapper<Containers, List<String>> TRANSFORMER_CONTAINER_ARGS_FIELD = new FieldMapper<>(
            ArrayList::new,
            Containers::getArgs,
            Containers::setArgs);

    public static final FieldMapper<Containers, List<io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Env>>
            TRANSFORMER_CONTAINER_ENV_FIELD = new FieldMapper<>(
                    ArrayList::new,
                    Containers::getEnv,
                    Containers::setEnv);

    public static final NamedItemMapper<io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Env>
            TRANSFORMER_ENV_VAR_NAME = new NamedItemMapper<>(
                    io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Env::new,
                    io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Env::getName,
                    io.kserve.serving.v1beta1.inferenceservicespec.transformer.containers.Env::setName);

}
