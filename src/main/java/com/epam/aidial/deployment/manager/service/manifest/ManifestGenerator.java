package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.AppProperties;
import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.service.RegistryService;
import com.epam.aidial.deployment.manager.utils.mapping.Mappers;
import io.fabric8.kubernetes.api.model.Secret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@LogExecution
public class ManifestGenerator extends BaseManifestGenerator {

    public static final String DOCKER_CONFIG_KEY = "config.json";

    private final RegistryService registryService;

    public ManifestGenerator(AppProperties appconfig, RegistryService registryService) {
        super(appconfig);
        this.registryService = registryService;
    }

    public Secret dialRegistryAuthSecretConfig(String name) {
        var dockerConfigData = new HashMap<String, String>();

        if (registryService.getAuthScheme() == DockerAuthScheme.BASIC) {
            dockerConfigData.put(DOCKER_CONFIG_KEY, registryService.dockerConfig());
        }

        return secretConfig(name, dockerConfigData, null);
    }

    public Secret secretConfig(String name, Map<String, String> stringData, Map<String, String> binaryData) {
        var config = createBaseManifestChain(
                appConfig::cloneBuilderSecretConfig,
                chain -> chain.get(Mappers.SECRET_METADATA_FIELD),
                name
        );

        if (stringData != null) {
            config.data().setStringData(stringData);
        }

        if (binaryData != null) {
            config.data().setData(binaryData);
        }

        return config.data();
    }

}
