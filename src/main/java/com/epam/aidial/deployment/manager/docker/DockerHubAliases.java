package com.epam.aidial.deployment.manager.docker;

import java.util.Objects;
import java.util.Set;

public final class DockerHubAliases {

    public static final String LEGACY_AUTH_KEY = "https://index.docker.io/v1/";

    private static final Set<String> HOSTS = Set.of(
            "docker.io", "index.docker.io", "registry-1.docker.io");

    private DockerHubAliases() {
    }

    public static boolean contains(String host) {
        return host != null && HOSTS.contains(host);
    }

    public static boolean sameRegistry(String left, String right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        return contains(left) && contains(right);
    }
}
