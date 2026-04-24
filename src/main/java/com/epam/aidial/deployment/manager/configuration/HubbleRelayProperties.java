package com.epam.aidial.deployment.manager.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Hubble Relay gRPC integration.
 *
 * <p>All defaults are declared in {@code application.yml}; no Java-level initializers are used.
 *
 * <p>The {@code host} property targets the direct-connect (NodePort/LoadBalancer) path where the
 * gRPC channel connects to {@code host:port} directly. In the default port-forward mode,
 * {@code HubbleRelayGrpcChannelFactory} opens a {@code LocalPortForward} and binds the channel to
 * {@code localhost:localPort} instead; {@code host} is preserved for future use.
 */
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
