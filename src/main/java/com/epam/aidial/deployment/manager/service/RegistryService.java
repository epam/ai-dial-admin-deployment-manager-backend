package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.configuration.RegistryProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.docker.DockerHubAliases;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;

@Slf4j
@Service
@LogExecution
public class RegistryService {

    private static final String API_URL_TEMPLATE = "%s://%s/v2";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegistryProperties registryProperties;
    private final String imageFormat;

    public RegistryService(RegistryProperties registryProperties,
                          @Value("${app.image-name-format}") String imageFormat) {
        this.registryProperties = registryProperties;
        this.imageFormat = imageFormat;
    }

    @PostConstruct
    public void validate() {
        if (registryProperties.getAuth() == DockerAuthScheme.BASIC
                && (StringUtils.isBlank(registryProperties.getUser()) || registryProperties.getPassword() == null)) {
            throw new IllegalStateException("User and password are required for BASIC docker registry authentication.");
        }
    }

    public String fullImageName(String name, String version) {
        return "%s/%s:%s".formatted(registryProperties.getUrl(), imageName(name), version);
    }

    @SneakyThrows
    public String dockerConfig() {
        Map<String, Object> configMap = new HashMap<>();
        Map<String, Object> auths = new HashMap<>();

        // Add the main registry authentication if using BASIC auth
        if (registryProperties.getAuth() == DockerAuthScheme.BASIC) {
            byte[] bytes = "%s:%s".formatted(registryProperties.getUser(), registryProperties.getPassword())
                    .getBytes(StandardCharsets.UTF_8);
            String auth = Base64.getEncoder().encodeToString(bytes);

            Map<String, String> authConfig = new HashMap<>();
            authConfig.put("auth", auth);

            auths.put(authKey(registryProperties.getProtocol().toString(), registryProperties.getUrl()), authConfig);
        }

        // Add trusted private registries if configured
        var trustedRegistries = registryProperties.getTrustedPrivateRegistries();
        for (var trustedRegistry : trustedRegistries) {
            if ("BASIC".equals(trustedRegistry.getAuthScheme())) {
                String regUser = trustedRegistry.getUser();
                String regPassword = trustedRegistry.getPassword();

                if (StringUtils.isNotBlank(regUser) && regPassword != null) {
                    byte[] regBytes = "%s:%s".formatted(regUser, regPassword).getBytes(StandardCharsets.UTF_8);
                    String regAuth = Base64.getEncoder().encodeToString(regBytes);

                    Map<String, String> authConfig = new HashMap<>();
                    authConfig.put("auth", regAuth);

                    String regProtocol = StringUtils.isNotBlank(trustedRegistry.getProtocol())
                            ? trustedRegistry.getProtocol()
                            : "https";
                    String key = authKey(regProtocol, trustedRegistry.getRegistry());
                    if (auths.containsKey(key)) {
                        log.warn("Multiple registry entries (main or trusted-private) collapse to the same auth key '{}' "
                                + "(e.g. Docker Hub aliases docker.io / index.docker.io / registry-1.docker.io, "
                                + "or equivalent host strings for any registry). "
                                + "Later entry overwrites the earlier one — verify only one set of credentials is intended.", key);
                    }
                    auths.put(key, authConfig);
                }
            }
            // Add support for TOKEN auth scheme if needed
        }

        configMap.put("auths", auths);
        return MAPPER.writeValueAsString(configMap);
    }

    public DockerAuthScheme getAuthScheme() {
        return registryProperties.getAuth();
    }

    private String imageName(String name) {
        return imageFormat.formatted(name);
    }

    private static String authKey(String protocol, String registry) {
        return DockerHubAliases.contains(registry)
                ? DockerHubAliases.LEGACY_AUTH_KEY
                : API_URL_TEMPLATE.formatted(protocol, registry);
    }

}
