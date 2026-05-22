package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
import com.epam.aidial.deployment.manager.model.Resources;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.model.deployment.InferenceTask;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.utils.mapping.InferenceMappers;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.kserve.serving.v1beta1.InferenceService;
import io.kserve.serving.v1beta1.inferenceservicespec.Predictor;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Affinity;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Model;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Tolerations;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.Env;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.Ports;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.env.ValueFrom;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.env.valuefrom.SecretKeyRef;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@LogExecution
public class InferenceManifestGenerator extends DeployableManifestGenerator {

    private static final String MODEL_NAME_ARGUMENT_NAME = "--model_name";
    private static final String RETURN_RAW_LOGITS_ARG = "--return_raw_logits";
    private static final String RETURN_PROBABILITIES_ARG = "--return_probabilities";
    private static final String TASK_ARG = "--task";
    private static final String SEQUENCE_CLASSIFICATION_TASK = "sequence_classification";
    private static final String KSERVE_V2_PROTOCOL = "v2";

    private final KserveProbeConverter kserveProbeConverter;
    private final ProgressDeadlineCalculator progressDeadlineCalculator;
    private final PoolPrimitivesConverter poolPrimitivesConverter;
    private final TextClassificationTransformerSection textClassificationTransformerSection;

    public InferenceManifestGenerator(AppProperties appconfig,
                                     KserveProbeConverter kserveProbeConverter,
                                     ProgressDeadlineCalculator progressDeadlineCalculator,
                                     PoolPrimitivesConverter poolPrimitivesConverter,
                                     TextClassificationTransformerSection textClassificationTransformerSection) {
        super(appconfig);
        this.kserveProbeConverter = kserveProbeConverter;
        this.progressDeadlineCalculator = progressDeadlineCalculator;
        this.poolPrimitivesConverter = poolPrimitivesConverter;
        this.textClassificationTransformerSection = textClassificationTransformerSection;
    }

    public InferenceService serviceConfig(
            String name,
            String serviceName,
            String modelFormat,
            String storageUri,
            List<SimpleEnvVar> envs,
            List<SensitiveEnvVar> sensitiveEnv,
            Resources resources,
            @Nullable Scaling scaling,
            @Nullable List<String> command,
            @Nullable List<String> args,
            @Nullable Integer containerPort,
            @Nullable ProbeProperties probeProperties,
            int startupTimeoutSec,
            PoolSchedulingPrimitives poolPrimitives,
            @Nullable InferenceTask detectedTask,
            @Nullable Map<Integer, String> detectedId2Label
    ) {
        var config = createBaseManifestChain(
                appConfig::cloneInferenceServiceConfig,
                chain -> chain.get(InferenceMappers.SERVICE_METADATA_FIELD),
                serviceName
        );

        var specChain = config.get(InferenceMappers.SERVICE_SPEC_FIELD);
        var predictorChain = specChain.get(InferenceMappers.SERVICE_SPEC_PREDICTOR_FIELD);

        applyScaling(name, scaling, predictorChain, config);
        applyProgressDeadline(probeProperties, startupTimeoutSec, config);

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

        applyStartupProbe(name, modelChain, probeProperties);

        applyPoolPrimitives(predictorChain, poolPrimitives);

        if (detectedTask == InferenceTask.TEXT_CLASSIFICATION) {
            applyChainedTransformer(name, detectedId2Label, modelChain, config.data());
        }

        return config.data();
    }

    /**
     * Configure the predictor and transformer halves of a chained text-classification deployment:
     *
     * <ul>
     *   <li>Pin the predictor's {@code protocolVersion} to v2.</li>
     *   <li>Reject operator-supplied predictor args that conflict with the chained contract
     *       ({@code --return_probabilities}; {@code --task=<non-sequence_classification>}).</li>
     *   <li>Inject {@code --return_raw_logits} and {@code --task=sequence_classification} into
     *       the predictor args.</li>
     *   <li>Build the transformer block via {@link TextClassificationTransformerSection}.</li>
     * </ul>
     */
    private void applyChainedTransformer(String name,
                                         @Nullable Map<Integer, String> id2Label,
                                         MappingChain<Model> modelChain,
                                         InferenceService service) {
        if (MapUtils.isEmpty(id2Label)) {
            throw new IllegalStateException("Cannot emit chained transformer for deployment '%s': id2Label is empty"
                    .formatted(name));
        }
        modelChain.data().setProtocolVersion(KSERVE_V2_PROTOCOL);

        var predictorArgs = modelChain.get(InferenceMappers.MODEL_ARGS_FIELD).data();
        rejectConflictingChainedArgs(name, predictorArgs);
        if (!isArgPresent(RETURN_RAW_LOGITS_ARG, predictorArgs)) {
            predictorArgs.add(RETURN_RAW_LOGITS_ARG);
        }
        if (!isArgPresent(TASK_ARG, predictorArgs)) {
            predictorArgs.add(TASK_ARG + "=" + SEQUENCE_CLASSIFICATION_TASK);
        }

        textClassificationTransformerSection.apply(service, name, id2Label);
    }

    private void rejectConflictingChainedArgs(String name, List<String> predictorArgs) {
        if (predictorArgs == null) {
            return;
        }
        for (int i = 0; i < predictorArgs.size(); i++) {
            String arg = predictorArgs.get(i);
            if (arg == null) {
                continue;
            }
            if (arg.equals(RETURN_PROBABILITIES_ARG) || arg.startsWith(RETURN_PROBABILITIES_ARG + "=")) {
                throw new IllegalArgumentException(("Inference deployment '%s' is a text-classification model"
                        + " and cannot use predictor arg '%s' — the chained transformer requires raw logits."
                        + " Remove this arg.").formatted(name, RETURN_PROBABILITIES_ARG));
            }
            if (arg.equals(TASK_ARG)) {
                // Split form: ["--task", "<value>"] — peek at the next token.
                String value = (i + 1 < predictorArgs.size() && predictorArgs.get(i + 1) != null)
                        ? predictorArgs.get(i + 1)
                        : "";
                rejectIfNonSequenceClassificationTask(name, value);
            } else if (arg.startsWith(TASK_ARG + "=")) {
                // Fused form: "--task=<value>".
                rejectIfNonSequenceClassificationTask(name, arg.substring(TASK_ARG.length() + 1));
            }
        }
    }

    private void rejectIfNonSequenceClassificationTask(String name, String value) {
        if (!SEQUENCE_CLASSIFICATION_TASK.equals(value)) {
            throw new IllegalArgumentException(("Inference deployment '%s' is a text-classification model"
                    + " and cannot override '--task' to '%s' — the chained transformer requires"
                    + " '--task=%s'. Remove this arg or set it to the required value.")
                    .formatted(name, value, SEQUENCE_CLASSIFICATION_TASK));
        }
    }

    private void applyPoolPrimitives(MappingChain<Predictor> predictorChain, PoolSchedulingPrimitives primitives) {
        if (primitives == null || primitives.isEmpty()) {
            return;
        }
        if (MapUtils.isNotEmpty(primitives.nodeSelector())) {
            predictorChain.data().setNodeSelector(primitives.nodeSelector());
        }
        var convertedAffinity = poolPrimitivesConverter.convertAffinity(primitives.affinity(), Affinity.class);
        if (convertedAffinity != null) {
            predictorChain.data().setAffinity(convertedAffinity);
        }
        var convertedTolerations = poolPrimitivesConverter.convertTolerations(primitives.tolerations(), Tolerations.class);
        if (CollectionUtils.isNotEmpty(convertedTolerations)) {
            var existing = predictorChain.data().getTolerations();
            var merged = new ArrayList<Tolerations>();
            if (existing != null) {
                merged.addAll(existing);
            }
            merged.addAll(convertedTolerations);
            predictorChain.data().setTolerations(merged);
        }
    }

    private void applyStartupProbe(String name,
                                   MappingChain<Model> modelChain,
                                   @Nullable ProbeProperties deploymentProbeProperties) {
        var probe = kserveProbeConverter.toKserveStartupProbe(deploymentProbeProperties);
        if (probe != null) {
            log.debug("Applying startup probe for Inference deployment '{}': {}", name, probe);
            modelChain.data().setStartupProbe(probe);
        }
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

    private void applyProgressDeadline(@Nullable ProbeProperties probeProperties,
                                       int startupTimeoutSec,
                                       MappingChain<InferenceService> config) {
        var progressDeadline = progressDeadlineCalculator.compute(probeProperties, startupTimeoutSec);
        var annotations = config.get(InferenceMappers.SERVICE_METADATA_FIELD)
                .get(InferenceMappers.METADATA_ANNOTATIONS_FIELD).data();
        annotations.put(KnativeAnnotations.PROGRESS_DEADLINE, progressDeadline);
    }

    private void applyScaling(String name,
                              @Nullable Scaling scaling,
                              MappingChain<Predictor> predictorChain,
                              MappingChain<InferenceService> config) {
        log.debug("Applying scaling for model '{}': {}", name, scaling);
        if (scaling == null) {
            return;
        }

        var predictor = predictorChain.data();
        predictor.setMinReplicas(scaling.getMinReplicas());
        predictor.setMaxReplicas(scaling.getMaxReplicas());
        log.trace("Set minReplicas={}, maxReplicas={} for model '{}'",
                scaling.getMinReplicas(), scaling.getMaxReplicas(), name);

        var initialScale = Math.max(scaling.getMinReplicas(), 1);
        var annotations = config.get(InferenceMappers.SERVICE_METADATA_FIELD)
                .get(InferenceMappers.METADATA_ANNOTATIONS_FIELD).data();
        annotations.put(KnativeAnnotations.INITIAL_SCALE, String.valueOf(initialScale));
        log.trace("Set min-scale={}, max-scale={}, initial-scale={} for Inference deployment '{}'",
                scaling.getMinReplicas(), scaling.getMaxReplicas(), initialScale, name);

        if (scaling.getScaleToZeroDelaySeconds() != null) {
            var delay = scaling.getScaleToZeroDelaySeconds();
            var delayStr = delay + "s";
            annotations.put(KnativeAnnotations.SCALE_TO_ZERO_RETENTION, delayStr);
            log.trace("Set annotation autoscaling.knative.dev/scale-to-zero-pod-retention-period={} for model '{}'",
                    delayStr, name);
        }

        if (scaling.getStrategy() == null) {
            return;
        }

        if (scaling.getStrategy().getType() == ScalingStrategyType.ACTIVE_REQUESTS) {
            predictor.setScaleMetric(Predictor.ScaleMetric.CONCURRENCY);
            predictor.setScaleTarget(scaling.getStrategy().getThreshold());
            log.trace("Applied strategy ACTIVE_REQUESTS: target={} for model '{}'",
                    scaling.getStrategy().getThreshold(), name);
        } else {
            throw new IllegalArgumentException("Scaling strategy '%s' is not supported. Supported strategies: %s"
                    .formatted(scaling.getStrategy().getType(), SUPPORTED_SCALING_STRATEGIES));
        }
    }

}
