package com.epam.aidial.deployment.manager.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "hubble-relay")
public class HubbleRelayProperties {

    private boolean enabled;
    private String host;
    private String namespace;
    private String podLabelSelector;
    private int port;
    private int connectRetryCount;
    private long connectRetryIntervalMs;
    private boolean tlsEnabled;
    private String caCertPath;
}
