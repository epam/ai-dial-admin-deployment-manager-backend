package com.epam.aidial.deployment.manager.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class RegistryConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public RegistryProperties registryProperties(
            @Value("${app.registry.url:test-docker-registry}") String url,
            @Value("${app.registry.protocol:https}") String protocol,
            @Value("${app.registry.auth:}") String auth,
            @Value("${app.registry.user:}") String user,
            @Value("${app.registry.password:}") String password,
            @Value("${app.registry.trusted-private-registries:}") String trustedPrivateRegistriesJson) {

        RegistryProperties properties = new RegistryProperties();
        properties.setUrl(url);
        properties.setProtocol(URI.create(protocol));
        properties.setAuth(DockerAuthScheme.valueOf(auth));
        properties.setUser(user);
        properties.setPassword(password);

        // Deserialize trusted private registries JSON string
        if (StringUtils.isBlank(trustedPrivateRegistriesJson)) {
            properties.setTrustedPrivateRegistries(new ArrayList<>());
        } else {
            try {
                List<RegistryProperties.TrustedPrivateRegistry> registries = MAPPER.readValue(
                        trustedPrivateRegistriesJson, new TypeReference<>() {
                        });
                properties.setTrustedPrivateRegistries(registries);
                log.debug("Successfully deserialized {} trusted private registry configurations", registries.size());
            } catch (Exception e) {
                log.error("Failed to parse trusted-private-registries JSON: {}", e.getMessage(), e);
                throw new IllegalArgumentException("Invalid JSON format for trusted-private-registries: " + e.getMessage(), e);
            }
        }

        return properties;
    }
}
