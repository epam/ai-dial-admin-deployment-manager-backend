package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.knative.deploy")
public class KnativeDeployProperties {
    private String namespace;
    private int startupTimeout;
    private int undeployTimeout;
    private long informerResyncInterval;
    private int readyGracePeriod;
}

