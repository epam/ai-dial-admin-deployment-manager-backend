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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class GitConfiguration {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AUTH_TYPE_SSH = "SSH";
    private static final String AUTH_TYPE_HTTP = "HTTPS/HTTP";

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
            List<GitPropertiesDto.TrustedPrivateGitRepoDto> repoDtos;
            try {
                repoDtos = MAPPER.readValue(trustedPrivateReposJson, new TypeReference<>() {
                });
            } catch (Exception e) {
                log.error("Failed to parse trusted-private-repos JSON: {}", e.getMessage(), e);
                throw new IllegalArgumentException("Invalid JSON format for trusted-private-repos: " + e.getMessage(), e);
            }

            // Validate configuration rules, read SSH key files, and convert to processed model.
            // Validation failures propagate as-is: they are configuration errors, not JSON syntax errors.
            List<GitProperties.TrustedPrivateGitRepo> processedRepos = new ArrayList<>();
            for (GitPropertiesDto.TrustedPrivateGitRepoDto repoDto : repoDtos) {
                validateRepoConfiguration(repoDto);
                String sshKeyContent = readSshKeyFile(repoDto);
                String sshKnownHostsContent = readSshKnownHostsFile(repoDto);
                GitProperties.TrustedPrivateGitRepo processedRepo = convertToProcessedModel(repoDto, sshKeyContent, sshKnownHostsContent);
                processedRepos.add(processedRepo);
            }

            validateNoDuplicateScopes(processedRepos);

            properties.setTrustedPrivateRepos(processedRepos);
            log.debug("Successfully deserialized and processed {} trusted private git repo configurations", processedRepos.size());
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
     * Rejects configurations where two or more entries define the identical normalized (host, path) scope
     * <em>for the same authentication type</em>, since credential resolution could not otherwise pick
     * between them unambiguously. Entries sharing a (host, path) scope are allowed when their
     * authentication types differ: SSH URLs only ever resolve to SSH-key entries and HTTPS/HTTP URLs only
     * ever resolve to user/token entries, so such a pair stays unambiguous (see
     * {@code GitService.matchRank}). An entry carrying both an SSH key and user/token credentials occupies
     * both authentication types at its scope.
     *
     * @param repos The processed repository configurations to check for duplicate scopes
     * @throws IllegalArgumentException if two or more entries share the same scope and authentication type
     */
    private void validateNoDuplicateScopes(List<GitProperties.TrustedPrivateGitRepo> repos) {
        Map<CredentialScope, List<Integer>> scopeToEntryIndices = new LinkedHashMap<>();
        for (int i = 0; i < repos.size(); i++) {
            GitProperties.TrustedPrivateGitRepo repo = repos.get(i);
            for (String authType : authTypes(repo)) {
                CredentialScope scope = new CredentialScope(repo.getHost(), repo.getPath(), authType);
                scopeToEntryIndices.computeIfAbsent(scope, key -> new ArrayList<>()).add(i);
            }
        }

        for (Map.Entry<CredentialScope, List<Integer>> scopeEntry : scopeToEntryIndices.entrySet()) {
            List<Integer> entryIndices = scopeEntry.getValue();
            if (entryIndices.size() > 1) {
                CredentialScope scope = scopeEntry.getKey();
                String path = scope.path() == null ? "(domain-wide)" : scope.path();
                String errorMsg = "Duplicate trusted-private-repos scope for host '%s', path '%s' and %s authentication at entries %s"
                        .formatted(scope.host(), path, scope.authType(), entryIndices);
                log.error(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
        }
    }

    /**
     * Returns the authentication types an entry can serve, using exactly the same predicates as
     * {@code GitService.matchRank} so validation and resolution agree on when two entries could collide.
     *
     * @param repo The processed repository configuration
     * @return The authentication types the entry provides material for (never empty for a valid entry)
     */
    private List<String> authTypes(GitProperties.TrustedPrivateGitRepo repo) {
        List<String> authTypes = new ArrayList<>();
        if (repo.getSshKey() != null) {
            authTypes.add(AUTH_TYPE_SSH);
        }
        if (repo.getUser() != null || repo.getToken() != null) {
            authTypes.add(AUTH_TYPE_HTTP);
        }
        return authTypes;
    }

    /**
     * The scope one configured entry occupies: a normalized host, an optional normalized path
     * (null = domain-wide) and the authentication type the entry serves at that scope.
     */
    private record CredentialScope(String host, String path, String authType) {
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
        processedRepo.setHost(GitScopeUtils.normalizeHost(repoDto.getHost()));
        processedRepo.setPath(GitScopeUtils.normalizePath(repoDto.getPath()));
        processedRepo.setProtocol(repoDto.getProtocol());
        processedRepo.setUser(repoDto.getUser());
        processedRepo.setPassword(repoDto.getPassword());
        processedRepo.setToken(repoDto.getToken());
        processedRepo.setSshKey(sshKeyContent);
        processedRepo.setSshKnownHosts(sshKnownHostsContent);
        return processedRepo;
    }

}
