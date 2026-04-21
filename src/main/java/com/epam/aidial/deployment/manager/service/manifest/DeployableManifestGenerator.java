package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.kubernetes.knative.KnativeAnnotations;
import com.epam.aidial.deployment.manager.model.Scaling;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.utils.mapping.ListMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class DeployableManifestGenerator extends BaseManifestGenerator {

    protected static final List<ScalingStrategyType> SUPPORTED_SCALING_STRATEGIES =
            List.of(ScalingStrategyType.ACTIVE_REQUESTS);

    public DeployableManifestGenerator(AppProperties appconfig) {
        super(appconfig);
    }

    protected void applyScalingAnnotations(String name, Scaling scaling, Map<String, String> annotations) {
        var initialScale = Math.max(scaling.getMinReplicas(), 1);
        annotations.put(KnativeAnnotations.INITIAL_SCALE, String.valueOf(initialScale));
        annotations.put(KnativeAnnotations.MIN_SCALE, String.valueOf(scaling.getMinReplicas()));
        annotations.put(KnativeAnnotations.MAX_SCALE, String.valueOf(scaling.getMaxReplicas()));
        log.trace("Set min-scale={}, max-scale={}, initial-scale={} for deployment '{}'",
                scaling.getMinReplicas(), scaling.getMaxReplicas(), initialScale, name);

        if (scaling.getScaleToZeroDelaySeconds() != null) {
            var delayStr = scaling.getScaleToZeroDelaySeconds() + "s";
            annotations.put(KnativeAnnotations.SCALE_TO_ZERO_RETENTION, delayStr);
            log.trace("Set scale-to-zero-pod-retention-period={} for deployment '{}'", delayStr, name);
        }

        if (scaling.getStrategy() == null) {
            return;
        }

        if (scaling.getStrategy().getType() == ScalingStrategyType.ACTIVE_REQUESTS) {
            annotations.put(KnativeAnnotations.AUTOSCALING_CLASS, KnativeAnnotations.AUTOSCALING_CLASS_KPA);
            annotations.put(KnativeAnnotations.AUTOSCALING_METRIC, KnativeAnnotations.AUTOSCALING_METRIC_CONCURRENCY);
            annotations.put(KnativeAnnotations.AUTOSCALING_TARGET, String.valueOf(scaling.getStrategy().getThreshold()));
            log.trace("Applied strategy ACTIVE_REQUESTS: metric=concurrency, target={} for deployment '{}'",
                    scaling.getStrategy().getThreshold(), name);
        } else {
            throw new IllegalArgumentException("Scaling strategy '%s' is not supported. Supported strategies: %s"
                    .formatted(scaling.getStrategy().getType(), SUPPORTED_SCALING_STRATEGIES));
        }
    }

    protected <T> void applyResourceMap(
            Map<String, T> targetMap,
            Map<String, String> sourceMap,
            Function<String, T> converter
    ) {
        if (MapUtils.isNotEmpty(sourceMap)) {
            Map<String, T> convertedResources = sourceMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> converter.apply(entry.getValue())));
            targetMap.putAll(convertedResources);
        }
    }

    protected <E> void applySimpleEnvs(
            ListMapper<E> listMapper,
            List<SimpleEnvVar> envs,
            BiConsumer<E, String> valueSetter
    ) {
        envs.forEach(simpleEnv -> {
            var envVarChain = listMapper.get(simpleEnv.getName());
            valueSetter.accept(envVarChain.data(), simpleEnv.getValue().getValue());
        });
    }

    protected <E, S> void applySensitiveEnvs(
            ListMapper<E> listMapper,
            List<SensitiveEnvVar> envs,
            BiConsumer<E, S> valueFromSetter,
            Function<SensitiveEnvVar, S> secretRefBuilder
    ) {
        envs.forEach(sensitiveEnv -> {
            var envVarChain = listMapper.get(sensitiveEnv.getName());
            S secretRef = secretRefBuilder.apply(sensitiveEnv);
            valueFromSetter.accept(envVarChain.data(), secretRef);
        });
    }

}
