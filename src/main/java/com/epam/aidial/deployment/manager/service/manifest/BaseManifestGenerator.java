package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.utils.mapping.MappingChain;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;
import java.util.function.Supplier;

@RequiredArgsConstructor
public abstract class BaseManifestGenerator {

    protected final AppProperties appConfig;

    protected <T extends HasMetadata> MappingChain<T> createBaseManifestChain(
            Supplier<T> cloner,
            Function<MappingChain<T>, MappingChain<ObjectMeta>> metadataPath,
            String name
    ) {
        var configChain = new MappingChain<>(cloner.get());
        metadataPath.apply(configChain)
                .data()
                .setName(name);
        return configChain;
    }

}
