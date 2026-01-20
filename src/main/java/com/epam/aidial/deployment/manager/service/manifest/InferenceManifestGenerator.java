package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.utils.K8sNamingUtils;
import com.epam.aidial.deployment.manager.utils.mapping.InferenceMappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Model;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.Env;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.Ports;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.env.ValueFrom;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.env.valuefrom.SecretKeyRef;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@LogExecution
public class InferenceManifestGenerator extends DeployableManifestGenerator {

    private static final String MODEL_NAME_ARGUMENT_NAME = "--model_name";

    public InferenceManifestGenerator(AppProperties appconfig) {
        super(appconfig);
    }

    public InferenceService serviceConfig(
            String name,
            String modelFormat,
            String storageUri,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            Resources resources,
            @Nullable Integer minScale,
            @Nullable Integer maxScale,
            @Nullable List<String> command,
            @Nullable List<String> args,
            @Nullable Integer containerPort
    ) {
        var config = createBaseManifestChain(
                appConfig::cloneInferenceServiceConfig,
                chain -> chain.get(InferenceMappers.SERVICE_METADATA_FIELD),
                K8sNamingUtils.generateName(name)
        );

        var specChain = config.get(InferenceMappers.SERVICE_SPEC_FIELD);
        var predictorChain = specChain.get(InferenceMappers.SERVICE_SPEC_PREDICTOR_FIELD);

        if (minScale != null) {
            predictorChain.data().setMinReplicas(minScale);
        }
        if (maxScale != null) {
            predictorChain.data().setMaxReplicas(maxScale);
        }

        var modelChain = predictorChain.get(InferenceMappers.PREDICTOR_MODEL_FIELD);
        modelChain.data().setStorageUri(storageUri);

        modelChain.get(InferenceMappers.MODEL_FORMAT_FIELD).data().setName(modelFormat);

        if (command != null) {
            modelChain.get(InferenceMappers.MODEL_COMMAND_FIELD).data().clear();
            modelChain.get(InferenceMappers.MODEL_COMMAND_FIELD).data().addAll(command);
        }

        if (args != null) {
            modelChain.get(InferenceMappers.MODEL_ARGS_FIELD).data().clear();
            modelChain.get(InferenceMappers.MODEL_ARGS_FIELD).data().addAll(args);
        }

        // Explicitly set model name to ensure the model uses the intended name.
        // If omitted, the inference service will default to the Kubernetes service name,
        // which may differ from the actual model name due to naming transformations.
        setModelNameIfNotSet(name, modelChain);

        var envListMapper = modelChain
                .getList(InferenceMappers.MODEL_ENV_FIELD, InferenceMappers.ENV_VAR_NAME);
        applySimpleEnvs(envListMapper, envs, Env::setValue);
        applySensitiveEnvs(envListMapper, sensitiveEnv, Env::setValueFrom, this::buildInferenceServiceSecretRef);

        var resourceLimitsChain = modelChain.get(InferenceMappers.MODEL_RESOURCES_FIELD)
                .get(InferenceMappers.RESOURCES_LIMITS_FIELD);
        var resourceRequestsChain = modelChain.get(InferenceMappers.MODEL_RESOURCES_FIELD)
                .get(InferenceMappers.RESOURCES_REQUESTS_FIELD);
        applyResourceMap(resourceLimitsChain.data(), resources.getLimits(), IntOrString::new);
        applyResourceMap(resourceRequestsChain.data(), resources.getRequests(), IntOrString::new);

        if (containerPort != null) {
            var ports = new Ports();
            ports.setContainerPort(containerPort);
            ports.setHostPort(containerPort);
            modelChain.data().setPorts(List.of(ports));
        }

        return config.data();
    }

    private void setModelNameIfNotSet(String modelName, MappingChain<Model> modelChain) {
        var command = modelChain.getNullable(InferenceMappers.MODEL_COMMAND_FIELD).data();
        var args = modelChain.getNullable(InferenceMappers.MODEL_ARGS_FIELD).data();

        if (isArgPresent(MODEL_NAME_ARGUMENT_NAME, command) || isArgPresent(MODEL_NAME_ARGUMENT_NAME, args)) {
            log.info("Argument {} is already set for model '{}', skipping.", MODEL_NAME_ARGUMENT_NAME, modelName);
            return;
        }
        modelChain.get(InferenceMappers.MODEL_ARGS_FIELD).data().addAll(List.of(MODEL_NAME_ARGUMENT_NAME, modelName));
    }

    private boolean isArgPresent(String targetArg, List<String> existingArgs) {
        if (existingArgs == null) {
            return false;
        }
        var targetArgWithEquals = targetArg + "=";
        return existingArgs.stream()
                .anyMatch(arg -> arg.equals(targetArg) || arg.startsWith(targetArgWithEquals));
    }

    private ValueFrom buildInferenceServiceSecretRef(SensitiveEnvVar env) {
        var secretKeyRef = new SecretKeyRef();
        secretKeyRef.setKey(env.getK8sSecretKey());
        secretKeyRef.setName(env.getK8sSecretName());
        var valueFrom = new ValueFrom();
        valueFrom.setSecretKeyRef(secretKeyRef);
        return valueFrom;
    }

}
