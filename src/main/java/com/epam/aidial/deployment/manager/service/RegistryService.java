package com.epam.aidial.deployment.manager.service;

import com.epam.aidial.deployment.manager.configuration.DockerAuthScheme;
import com.epam.aidial.deployment.manager.configuration.RegistryProperties;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
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
import java.util.Set;
import javax.annotation.PostConstruct;

@Slf4j
@Service
@LogExecution
public class RegistryService {

    private static final String API_URL_TEMPLATE = "%s://%s/v2";

    // BuildKit's auth lookup hardcodes this legacy key for Docker Hub (docker/cli's
    // getAuthConfigKey maps docker.io / index.docker.io -> "https://index.docker.io/v1/").
    // A /v2-shaped key for docker.io is silently ignored there, so we emit the legacy key
    // for every Docker Hub alias. Skopeo's containers/image normalizes both forms.
    private static final String DOCKER_HUB_AUTH_KEY = "https://index.docker.io/v1/";
    private static final Set<String> DOCKER_HUB_HOSTS = Set.of(
            "docker.io", "index.docker.io", "registry-1.docker.io");

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
                    auths.put(authKey(regProtocol, trustedRegistry.getRegistry()), authConfig);
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
        return DOCKER_HUB_HOSTS.contains(registry)
                ? DOCKER_HUB_AUTH_KEY
                : API_URL_TEMPLATE.formatted(protocol, registry);
    }

}
