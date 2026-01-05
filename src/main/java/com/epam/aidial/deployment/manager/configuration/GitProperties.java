package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GitProperties {

    private List<TrustedPrivateGitRepo> trustedPrivateRepos = new ArrayList<>();

    // File names for Git credentials and SSH keys
    private String gitCredentialsFile = ".git-credentials";
    private String gitConfigFile = ".gitconfig";
    private String sshKeyFile = "id_rsa";
    private String sshKnownHostsFile = "known_hosts";

    // Kubernetes volume configuration
    private String gitSecretVolumeName = "git-secret-volume";

    // Container paths
    private String rootHomeDir = "/root";
    private String sshDir = "/root/.ssh";

    @Data
    public static class TrustedPrivateGitRepo {
        private String host;
        private String protocol = "https";
        private String user;
        private String password;
        private String token;
        private String sshKey;
        private String sshKnownHosts;
    }
}
