package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.nim.deploy")
public class NimDeployProperties {

    private String namespace;
    private int startupTimeout;
    private long informerResyncInterval;
    private boolean useClusterInternalUrl;
    private String urlSchema;
}
