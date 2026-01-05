package com.epam.aidial.deployment.manager.configuration;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "app.kserve.deploy")
public class KserveDeployProperties {
    // https://github.com/kserve/kserve/issues/4807
    @Size(max = 14, message = "Max length for kserve namespace is 14 characters")
    private String namespace;
    private int startupTimeout;
    private boolean useClusterInternalUrl;
}

