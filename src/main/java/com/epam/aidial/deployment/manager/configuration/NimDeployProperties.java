package com.epam.aidial.deployment.manager.configuration;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
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
    private String clusterHost;
    private String urlSchema;
    private boolean kserveModeEnabled;

    @AssertTrue(message = "'cluster-host' must not be blank when 'use-cluster-internal-url' is false and 'kserve-mode-enabled' is false")
    public boolean isClusterHostValid() {
        return kserveModeEnabled || useClusterInternalUrl || StringUtils.isNotBlank(clusterHost);
    }
}
