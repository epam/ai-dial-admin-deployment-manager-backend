package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.model.ScalingStrategyType;
import com.epam.aidial.deployment.manager.model.SensitiveEnvVar;
import com.epam.aidial.deployment.manager.model.SimpleEnvVar;
import com.epam.aidial.deployment.manager.utils.mapping.ListMapper;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class DeployableManifestGenerator extends BaseManifestGenerator {

    protected static final List<ScalingStrategyType> SUPPORTED_SCALING_STRATEGIES =
            List.of(ScalingStrategyType.ACTIVE_REQUESTS);

    public DeployableManifestGenerator(AppProperties appconfig) {
        super(appconfig);
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
