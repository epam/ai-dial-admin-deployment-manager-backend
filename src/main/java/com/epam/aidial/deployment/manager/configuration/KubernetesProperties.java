package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.kubernetes")
public class KubernetesProperties {

    private ConnectType connectType;
    private ConfigFileConnection configFile;
    private TokenConnection token;

    @Data
    public static class ConfigFileConnection {
        private String kubeConfig;
        private Map<String, String> contexts;
    }

    @Data
    public static class TokenConnection {
        private String masterUrl;
        private String oauthToken;
    }

    public enum ConnectType {
        CONFIG_FILE, TOKEN
    }
}