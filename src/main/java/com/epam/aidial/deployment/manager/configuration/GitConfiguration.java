package com.epam.aidial.deployment.manager.configuration;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class GitConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public GitProperties gitProperties(
            @Value("${app.git.trusted-private-repos}") String trustedPrivateReposJson,
            @Value("${app.build.secrets.git.credentials-file}") String gitCredentialsFile,
            @Value("${app.build.secrets.git.config-file}") String gitConfigFile,
            @Value("${app.build.secrets.git.ssh-key-file}") String sshKeyFile,
            @Value("${app.build.secrets.git.ssh-known-hosts-file}") String sshKnownHostsFile,
            @Value("${app.build.secrets.git.secret-volume-name}") String gitSecretVolumeName,
            @Value("${app.build.secrets.git.root-home-dir}") String rootHomeDir) {

        GitProperties properties = new GitProperties();

        // Set file names and paths
        properties.setGitCredentialsFile(gitCredentialsFile);
        properties.setGitConfigFile(gitConfigFile);
        properties.setSshKeyFile(sshKeyFile);
        properties.setSshKnownHostsFile(sshKnownHostsFile);
        properties.setGitSecretVolumeName(gitSecretVolumeName);
        properties.setRootHomeDir(rootHomeDir);
        // SSH dir is derived from root home dir
        properties.setSshDir(rootHomeDir + "/.ssh");

        if (StringUtils.isBlank(trustedPrivateReposJson)) {
            properties.setTrustedPrivateRepos(new ArrayList<>());
        } else {
            try {
                List<GitPropertiesDto.TrustedPrivateGitRepoDto> repoDtos = MAPPER.readValue(trustedPrivateReposJson, new TypeReference<>() {
                });

                // Validate configuration rules, read SSH key files, and convert to processed model
                List<GitProperties.TrustedPrivateGitRepo> processedRepos = new ArrayList<>();
                for (GitPropertiesDto.TrustedPrivateGitRepoDto repoDto : repoDtos) {
                    validateRepoConfiguration(repoDto);
                    String sshKeyContent = readSshKeyFile(repoDto);
                    String sshKnownHostsContent = readSshKnownHostsFile(repoDto);
                    GitProperties.TrustedPrivateGitRepo processedRepo = convertToProcessedModel(repoDto, sshKeyContent, sshKnownHostsContent);
                    processedRepos.add(processedRepo);
                }

                properties.setTrustedPrivateRepos(processedRepos);
                log.debug("Successfully deserialized and processed {} trusted private git repo configurations", processedRepos.size());
            } catch (Exception e) {
                log.error("Failed to parse trusted-private-repos JSON: {}", e.getMessage(), e);
                throw new IllegalArgumentException("Invalid JSON format for trusted-private-repos: " + e.getMessage(), e);
            }
        }

        return properties;
    }

    /**
     * Validates the repository configuration according to the business rules.
     *
     * @param repoDto The repository configuration DTO to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRepoConfiguration(GitPropertiesDto.TrustedPrivateGitRepoDto repoDto) {
        String host = repoDto.getHost();
        boolean hasSshKey = repoDto.getSshKeyPath() != null;
        boolean hasSshKnownHosts = repoDto.getSshKnownHostsPath() != null;
        boolean hasUser = repoDto.getUser() != null;
        boolean hasPassword = repoDto.getPassword() != null;
        boolean hasToken = repoDto.getToken() != null;

        // Rule 1: Host must be set
        if (host == null) {
            String errorMsg = "Host must be set for repository configuration";
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Rule 2: Either user or sshKey must be set
        if (!hasUser && !hasSshKey) {
            String errorMsg = "Either user or sshKey must be set for host: %s".formatted(host);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Rule 3: sshKey and sshKnownHosts must be set at the same time
        if (hasSshKey != hasSshKnownHosts) {
            String errorMsg = "sshKey and sshKnownHosts must be set at the same time for host: %s".formatted(host);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Rule 4: If user is set, then either password or token must be set
        if (hasUser && !hasPassword && !hasToken) {
            String errorMsg = "If user is set, then either password or token must be set for host: %s".formatted(host);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Rule 5: If password is set, then user must be set
        if (hasPassword && !hasUser) {
            String errorMsg = "If password is set, then user must be set for host: %s".formatted(host);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * Reads SSH key file from file path and returns its content.
     * Fails if file is not found or cannot be read.
     *
     * @param repoDto The repository configuration DTO containing file path
     * @return The SSH key file content, or null if sshKey path is not set
     * @throws IllegalArgumentException if file is not found or cannot be read
     */
    private String readSshKeyFile(GitPropertiesDto.TrustedPrivateGitRepoDto repoDto) {
        if (repoDto.getSshKeyPath() == null) {
            return null;
        }
        String sshKeyPath = repoDto.getSshKeyPath();
        try {
            Path path = Paths.get(sshKeyPath);
            if (!Files.exists(path)) {
                String errorMsg = "SSH key file not found for host %s: %s".formatted(repoDto.getHost(), sshKeyPath);
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            String sshKeyContent = Files.readString(path);
            log.debug("Successfully read SSH key file for host {}: {}", repoDto.getHost(), sshKeyPath);
            return sshKeyContent;
        } catch (IOException e) {
            String errorMsg = "Failed to read SSH key file for host %s: %s".formatted(repoDto.getHost(), sshKeyPath);
            log.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }
    }

    /**
     * Reads SSH known hosts file from file path and returns its content.
     * Fails if file is not found or cannot be read.
     *
     * @param repoDto The repository configuration DTO containing file path
     * @return The SSH known hosts file content, or null if sshKnownHosts path is not set
     * @throws IllegalArgumentException if file is not found or cannot be read
     */
    private String readSshKnownHostsFile(GitPropertiesDto.TrustedPrivateGitRepoDto repoDto) {
        if (repoDto.getSshKnownHostsPath() == null) {
            return null;
        }
        String sshKnownHostsPath = repoDto.getSshKnownHostsPath();
        try {
            Path path = Paths.get(sshKnownHostsPath);
            if (!Files.exists(path)) {
                String errorMsg = "SSH known hosts file not found for host %s: %s".formatted(repoDto.getHost(), sshKnownHostsPath);
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
            String sshKnownHostsContent = Files.readString(path);
            log.debug("Successfully read SSH known hosts file for host {}: {}", repoDto.getHost(), sshKnownHostsPath);
            return sshKnownHostsContent;
        } catch (IOException e) {
            String errorMsg = "Failed to read SSH known hosts file for host %s: %s".formatted(repoDto.getHost(), sshKnownHostsPath);
            log.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }
    }

    /**
     * Converts a DTO to the processed model with file contents instead of file paths.
     *
     * @param repoDto              The repository configuration DTO
     * @param sshKeyContent        The SSH key file content (read from file path)
     * @param sshKnownHostsContent The SSH known hosts file content (read from file path)
     * @return The processed repository configuration
     */
    private GitProperties.TrustedPrivateGitRepo convertToProcessedModel(
            GitPropertiesDto.TrustedPrivateGitRepoDto repoDto,
            String sshKeyContent,
            String sshKnownHostsContent) {
        GitProperties.TrustedPrivateGitRepo processedRepo = new GitProperties.TrustedPrivateGitRepo();
        processedRepo.setHost(repoDto.getHost());
        processedRepo.setProtocol(repoDto.getProtocol());
        processedRepo.setUser(repoDto.getUser());
        processedRepo.setPassword(repoDto.getPassword());
        processedRepo.setToken(repoDto.getToken());
        processedRepo.setSshKey(sshKeyContent);
        processedRepo.setSshKnownHosts(sshKnownHostsContent);
        return processedRepo;
    }

}
