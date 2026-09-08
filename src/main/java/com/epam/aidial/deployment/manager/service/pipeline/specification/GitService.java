package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.GitProperties;
import com.epam.aidial.deployment.manager.configuration.GitScopeUtils;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.GitSecretConfig;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class GitService {

    private final GitProperties gitProperties;

    /**
     * Prepares Git secret configuration for a given Git URL.
     * Returns an Optional containing GitSecretConfig with the secret and metadata about how to attach it to containers.
     * If no credentials are needed, returns Optional.empty().
     *
     * @param gitUrl            The git repository URL
     * @param secretName        The name to use for the Kubernetes secret
     * @param manifestGenerator The manifest generator to create the secret
     * @return Optional containing GitSecretConfig with the secret and volume mount metadata, or empty if not needed
     */
    public Optional<GitSecretConfig> prepareGitSecret(String gitUrl, String secretName, ManifestGenerator manifestGenerator) {
        if (!needsGitCredentials(gitUrl)) {
            return Optional.empty();
        }

        if (isSshUrl(gitUrl)) {
            return prepareSshSecret(gitUrl, secretName, manifestGenerator);
        } else {
            return prepareHttpsSecret(gitUrl, secretName, manifestGenerator);
        }
    }

    private Optional<GitSecretConfig> prepareSshSecret(String gitUrl, String secretName, ManifestGenerator manifestGenerator) {
        var gitSecretData = new HashMap<String, String>();

        var sshKey = generateSshKey(gitUrl);
        gitSecretData.put(gitProperties.getSshKeyFile(), sshKey);

        var sshKnownHosts = generateSshKnownHosts(gitUrl);
        gitSecretData.put(gitProperties.getSshKnownHostsFile(), sshKnownHosts);

        var volumeMounts = List.of(
                GitSecretConfig.VolumeMountConfig.builder()
                        .mountPath(gitProperties.getSshDir() + "/" + gitProperties.getSshKeyFile())
                        .subPath(gitProperties.getSshKeyFile())
                        .readOnly(true)
                        .build(),
                GitSecretConfig.VolumeMountConfig.builder()
                        .mountPath(gitProperties.getSshDir() + "/" + gitProperties.getSshKnownHostsFile())
                        .subPath(gitProperties.getSshKnownHostsFile())
                        .readOnly(true)
                        .build()
        );

        var secret = manifestGenerator.secretConfig(secretName, gitSecretData, null);
        var config = GitSecretConfig.builder()
                .secret(secret)
                .secretName(secretName)
                .volumeName(gitProperties.getGitSecretVolumeName())
                .volumeMounts(volumeMounts)
                .defaultMode(0600)
                .build();

        return Optional.of(config);
    }


    private Optional<GitSecretConfig> prepareHttpsSecret(String gitUrl, String secretName, ManifestGenerator manifestGenerator) {
        var gitSecretData = new HashMap<String, String>();
        var volumeMounts = new ArrayList<GitSecretConfig.VolumeMountConfig>();

        var gitCredentials = generateGitCredentials(gitUrl);
        gitSecretData.put(gitProperties.getGitCredentialsFile(), gitCredentials);
        volumeMounts.add(GitSecretConfig.VolumeMountConfig.builder()
                .mountPath(gitProperties.getRootHomeDir() + "/" + gitProperties.getGitCredentialsFile())
                .subPath(gitProperties.getGitCredentialsFile())
                .readOnly(true)
                .build());

        var gitConfig = generateGitConfig();
        gitSecretData.put(gitProperties.getGitConfigFile(), gitConfig);
        volumeMounts.add(GitSecretConfig.VolumeMountConfig.builder()
                .mountPath(gitProperties.getRootHomeDir() + "/" + gitProperties.getGitConfigFile())
                .subPath(gitProperties.getGitConfigFile())
                .readOnly(true)
                .build());

        var secret = manifestGenerator.secretConfig(secretName, gitSecretData, null);
        var config = GitSecretConfig.builder()
                .secret(secret)
                .secretName(secretName)
                .volumeName(gitProperties.getGitSecretVolumeName())
                .volumeMounts(volumeMounts)
                .build();

        return Optional.of(config);
    }

    /**
     * Generates git credentials file content for trusted private repos.
     * Format: protocol://username:password@host/path or protocol://username:token@host/path (one per line)
     * For token-based auth, uses username:token format if username is provided, otherwise uses token@host format.
     *
     * @param gitUrl The git repository URL to check against trusted repos
     * @return Git credentials file content, or empty string if no credentials needed
     */
    private String generateGitCredentials(String gitUrl) {
        return findMatchingTrustedRepo(gitUrl)
                .map(trustedRepo -> {
                    var host = extractHostFromHttpUrl(gitUrl);

                    // Use token if available, otherwise use user/password
                    if (StringUtils.isNotBlank(trustedRepo.getToken())) {
                        // Token-based authentication
                        // Use username:token format if username is provided (more compatible with Git providers)
                        if (StringUtils.isNotBlank(trustedRepo.getUser())) {
                            return "%s://%s:%s@%s\n".formatted(
                                    trustedRepo.getProtocol(),
                                    trustedRepo.getUser(),
                                    trustedRepo.getToken(),
                                    host);
                        } else {
                            // Fallback to token-only format
                            return "%s://%s@%s\n".formatted(trustedRepo.getProtocol(), trustedRepo.getToken(), host);
                        }
                    } else if (StringUtils.isNotBlank(trustedRepo.getUser())
                            && StringUtils.isNotBlank(trustedRepo.getPassword())) {
                        // Basic authentication with user/password
                        return "%s://%s:%s@%s\n".formatted(
                                trustedRepo.getProtocol(),
                                trustedRepo.getUser(),
                                trustedRepo.getPassword(),
                                host);
                    }
                    return null;
                })
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("GIT credentials are not configured for URL: " + gitUrl));
    }

    private String generateGitConfig() {
        return """
                [credential]
                    helper = store
                """;
    }

    private String generateSshKey(String gitUrl) {
        return findMatchingTrustedRepo(gitUrl)
                .map(GitProperties.TrustedPrivateGitRepo::getSshKey)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("SSH key is not configured for URL: " + gitUrl));
    }

    private String generateSshKnownHosts(String gitUrl) {
        return findMatchingTrustedRepo(gitUrl)
                .map(GitProperties.TrustedPrivateGitRepo::getSshKnownHosts)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("SSH known hosts are not configured for URL: " + gitUrl));
    }

    private boolean needsGitCredentials(String gitUrl) {
        return findMatchingTrustedRepo(gitUrl).isPresent();
    }

    private boolean isSshUrl(String gitUrl) {
        return gitUrl.startsWith("git@") || gitUrl.startsWith("ssh://");
    }

    private String extractHostFromUrl(String gitUrl) {
        if (isSshUrl(gitUrl)) {
            return extractHostFromSshUrl(gitUrl);
        } else {
            return extractHostFromHttpUrl(gitUrl);
        }
    }

    /**
     * Extracts host from an SSH-formatted git URL (git@host:path or ssh://git@host/path).
     *
     * @param gitUrl The SSH-formatted git repository URL
     * @return The host extracted from the URL, or null if parsing fails
     */
    private String extractHostFromSshUrl(String gitUrl) {
        if (gitUrl.startsWith("git@")) {
            // Format: git@host:path
            var colonIndex = gitUrl.indexOf(':');
            if (colonIndex > 0) {
                return gitUrl.substring(4, colonIndex); // Skip "git@"
            }
        } else if (gitUrl.startsWith("ssh://")) {
            // Format: ssh://git@host/path or ssh://host/path
            var withoutProtocol = gitUrl.substring(6); // Skip "ssh://"
            int slashIndex = withoutProtocol.indexOf('/');
            if (slashIndex > 0) {
                var hostPart = withoutProtocol.substring(0, slashIndex);
                if (hostPart.startsWith("git@")) {
                    return hostPart.substring(4); // Skip "git@"
                }
                return hostPart;
            }
        }
        throw new IllegalArgumentException("Failed to parse SSH URL: " + gitUrl);
    }

    private String extractHostFromHttpUrl(String gitUrl) {
        try {
            var uri = URI.create(gitUrl);
            return uri.getHost();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse HTTP URL: " + gitUrl, e);
        }
    }

    /**
     * Extracts the repository path from a git URL (HTTPS or SSH form), normalized for scope matching.
     *
     * @param gitUrl The git repository URL
     * @return The normalized repository path, or null if the URL carries no path
     */
    private String extractPathFromUrl(String gitUrl) {
        String path = isSshUrl(gitUrl) ? extractPathFromSshUrl(gitUrl) : extractPathFromHttpUrl(gitUrl);
        return GitScopeUtils.normalizePath(path);
    }

    /**
     * Extracts the path portion from an SSH-formatted git URL (git@host:path or ssh://host/path).
     *
     * @param gitUrl The SSH-formatted git repository URL
     * @return The path extracted from the URL
     */
    private String extractPathFromSshUrl(String gitUrl) {
        if (gitUrl.startsWith("git@")) {
            var colonIndex = gitUrl.indexOf(':');
            if (colonIndex > 0) {
                return gitUrl.substring(colonIndex + 1);
            }
        } else if (gitUrl.startsWith("ssh://")) {
            var withoutProtocol = gitUrl.substring(6);
            int slashIndex = withoutProtocol.indexOf('/');
            if (slashIndex > 0) {
                return withoutProtocol.substring(slashIndex + 1);
            }
        }
        throw new IllegalArgumentException("Failed to parse SSH URL: " + gitUrl);
    }

    private String extractPathFromHttpUrl(String gitUrl) {
        try {
            var uri = URI.create(gitUrl);
            return uri.getPath();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse HTTP URL: " + gitUrl, e);
        }
    }

    /**
     * Finds the most specific matching trusted private repo configuration for the given git URL.
     * When several configured entries match (e.g. a domain-wide entry and a project- or
     * repository-scoped entry on the same host), the most specific one wins: an exact-host match
     * beats a subdomain-inherited match, and — within the same host-match level — a repository-exact
     * scope beats a project/group scope, which beats a domain-wide scope.
     *
     * @param gitUrl The git repository URL
     * @return Optional containing the best-matching TrustedPrivateGitRepo, or empty if none match
     */
    private Optional<GitProperties.TrustedPrivateGitRepo> findMatchingTrustedRepo(String gitUrl) {
        if (gitProperties.getTrustedPrivateRepos().isEmpty()) {
            return Optional.empty();
        }

        try {
            var host = GitScopeUtils.normalizeHost(extractHostFromUrl(gitUrl));
            var path = extractPathFromUrl(gitUrl);

            GitProperties.TrustedPrivateGitRepo bestMatch = null;
            int[] bestRank = null;
            for (var trustedRepo : gitProperties.getTrustedPrivateRepos()) {
                int[] rank = matchRank(host, path, gitUrl, trustedRepo);
                if (rank != null && (bestRank == null || isMoreSpecific(rank, bestRank))) {
                    bestRank = rank;
                    bestMatch = trustedRepo;
                }
            }
            return Optional.ofNullable(bestMatch);
        } catch (Exception e) {
            log.warn("Failed to parse git URL for trusted repo lookup: {}", gitUrl, e);
        }

        return Optional.empty();
    }

    /**
     * Computes a specificity rank for a candidate entry against the URL's (host, path), or returns
     * null if the entry does not match at all (wrong host, wrong path scope, or wrong auth type for
     * the URL's protocol). Rank components, most significant first: host-match level (0 = exact host,
     * 1 = subdomain-inherited), configured host length (longer = more specific parent domain, only
     * relevant when comparing two subdomain-inherited matches), path-match level (2 = repository-exact,
     * 1 = project/group prefix, 0 = domain-wide), and configured path length (longer prefix wins among
     * nested project scopes).
     *
     * @param urlHost    The normalized host extracted from the git URL
     * @param urlPath    The normalized path extracted from the git URL, or null if the URL has no path
     * @param gitUrl     The original git URL, used only to decide SSH vs. HTTPS auth-type matching
     * @param trustedRepo The candidate trusted repo configuration
     * @return a 4-element specificity rank, or null if the entry does not match
     */
    private int[] matchRank(String urlHost, String urlPath, String gitUrl, GitProperties.TrustedPrivateGitRepo trustedRepo) {
        var entryHost = trustedRepo.getHost();
        int hostRank;
        if (urlHost.equals(entryHost)) {
            hostRank = 0;
        } else if (urlHost.endsWith("." + entryHost)) {
            hostRank = 1;
        } else {
            return null;
        }

        var entryPath = trustedRepo.getPath();
        int pathRank;
        int pathLength;
        if (entryPath == null) {
            pathRank = 0;
            pathLength = 0;
        } else if (entryPath.equals(urlPath)) {
            pathRank = 2;
            pathLength = entryPath.length();
        } else if (urlPath != null && urlPath.startsWith(entryPath + "/")) {
            pathRank = 1;
            pathLength = entryPath.length();
        } else {
            return null;
        }

        // For SSH URLs, only match repos with SSH key authentication; for HTTPS/HTTP, only user or token
        if (isSshUrl(gitUrl) ? trustedRepo.getSshKey() == null : (trustedRepo.getUser() == null && trustedRepo.getToken() == null)) {
            return null;
        }

        return new int[] {hostRank, entryHost.length(), pathRank, pathLength};
    }

    private boolean isMoreSpecific(int[] candidate, int[] currentBest) {
        if (candidate[0] != currentBest[0]) {
            return candidate[0] < currentBest[0];
        }
        if (candidate[1] != currentBest[1]) {
            return candidate[1] > currentBest[1];
        }
        if (candidate[2] != currentBest[2]) {
            return candidate[2] > currentBest[2];
        }
        return candidate[3] > currentBest[3];
    }

}
