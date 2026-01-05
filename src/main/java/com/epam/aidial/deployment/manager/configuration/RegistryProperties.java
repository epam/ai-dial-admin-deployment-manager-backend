package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Data
public class RegistryProperties {

    private String url;
    private URI protocol;
    private DockerAuthScheme auth;
    private String user;
    private String password;
    private List<TrustedPrivateRegistry> trustedPrivateRegistries = new ArrayList<>();

    @Data
    public static class TrustedPrivateRegistry {
        private String registry;
        private String authScheme;
        private String protocol = "https";
        private String user;
        private String password;
    }
}

