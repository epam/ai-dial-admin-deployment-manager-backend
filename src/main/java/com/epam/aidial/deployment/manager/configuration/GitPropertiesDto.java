package com.epam.aidial.deployment.manager.configuration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GitPropertiesDto {

    private List<TrustedPrivateGitRepoDto> trustedPrivateRepos = new ArrayList<>();

    @Data
    public static class TrustedPrivateGitRepoDto {
        private String host;
        private String protocol = "https";
        private String user;
        private String password;
        private String token;
        private String sshKeyPath;
        private String sshKnownHostsPath;
    }
}
