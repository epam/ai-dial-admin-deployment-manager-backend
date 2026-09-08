package com.epam.aidial.deployment.manager.service.pipeline.specification;

import com.epam.aidial.deployment.manager.configuration.GitProperties;
import com.epam.aidial.deployment.manager.model.GitSecretConfig;
import com.epam.aidial.deployment.manager.service.manifest.ManifestGenerator;
import io.fabric8.kubernetes.api.model.Secret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitServiceTest {

    @Mock
    private GitProperties gitProperties;
    @Mock
    private ManifestGenerator manifestGenerator;

    @InjectMocks
    private GitService gitService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Set up default values for configurable properties (lenient to avoid UnnecessaryStubbingException)
        org.mockito.Mockito.lenient().when(gitProperties.getGitCredentialsFile()).thenReturn(".git-credentials");
        org.mockito.Mockito.lenient().when(gitProperties.getGitConfigFile()).thenReturn(".gitconfig");
        org.mockito.Mockito.lenient().when(gitProperties.getSshKeyFile()).thenReturn("id_rsa");
        org.mockito.Mockito.lenient().when(gitProperties.getSshKnownHostsFile()).thenReturn("known_hosts");
        org.mockito.Mockito.lenient().when(gitProperties.getGitSecretVolumeName()).thenReturn("git-secret-volume");
        org.mockito.Mockito.lenient().when(gitProperties.getRootHomeDir()).thenReturn("/root");
        org.mockito.Mockito.lenient().when(gitProperties.getSshDir()).thenReturn("/root/.ssh");
    }

    @Test
    void prepareGitSecret_shouldReturnEmpty_whenNoTrustedReposConfigured() {
        // Given
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(new ArrayList<>());

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "test-secret", manifestGenerator);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void prepareGitSecret_shouldReturnEmpty_whenUrlDoesNotMatchAnyTrustedRepo() {
        // Given
        var trustedRepo = createTrustedRepo("gitlab.com", "https", "user", "pass", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "test-secret", manifestGenerator);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void prepareGitSecret_shouldReturnHttpsSecret_whenHttpsUrlMatchesTrustedRepoWithUserAndPassword() {
        // Given
        var mockSecret = new Secret();
        var trustedRepo = createTrustedRepo("github.com", "https", "testuser", "testpass", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));
        when(manifestGenerator.secretConfig(anyString(), anyMap(), isNull())).thenReturn(mockSecret);

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        GitSecretConfig config = result.get();
        assertThat(config.getSecretName()).isEqualTo("git-secret");
        assertThat(config.getVolumeName()).isEqualTo("git-secret-volume");
        assertThat(config.getSecret()).isEqualTo(mockSecret);
        assertThat(config.getVolumeMounts()).hasSize(2);
        assertThat(config.getVolumeMounts().get(0).getMountPath()).isEqualTo("/root/.git-credentials");
        assertThat(config.getVolumeMounts().get(0).getSubPath()).isEqualTo(".git-credentials");
        assertThat(config.getVolumeMounts().get(0).isReadOnly()).isTrue();
        assertThat(config.getVolumeMounts().get(1).getMountPath()).isEqualTo("/root/.gitconfig");
        assertThat(config.getVolumeMounts().get(1).getSubPath()).isEqualTo(".gitconfig");
        assertThat(config.getVolumeMounts().get(1).isReadOnly()).isTrue();
        assertThat(config.getDefaultMode()).isNull();

        verify(manifestGenerator).secretConfig(eq("git-secret"), anyMap(), isNull());
    }

    @Test
    void prepareGitSecret_shouldReturnHttpsSecret_whenHttpsUrlMatchesTrustedRepoWithToken() {
        // Given
        var trustedRepo = createTrustedRepo("github.com", "https", null, null, "testtoken", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        GitSecretConfig config = result.get();
        assertThat(config.getSecretName()).isEqualTo("git-secret");
        assertThat(config.getVolumeMounts()).hasSize(2);
    }

    @Test
    void prepareGitSecret_shouldReturnHttpsSecret_whenHttpsUrlMatchesTrustedRepoWithUserAndToken() {
        // Given
        var trustedRepo = createTrustedRepo("github.com", "https", "testuser", null, "testtoken", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        GitSecretConfig config = result.get();
        assertThat(config.getSecretName()).isEqualTo("git-secret");
        assertThat(config.getVolumeMounts()).hasSize(2);
    }

    @Test
    void prepareGitSecret_shouldReturnSshSecret_whenSshUrlMatchesTrustedRepo() {
        // Given
        var mockSecret = new Secret();
        var sshKey = "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----";
        var sshKnownHosts = "github.com ssh-rsa AAABBBCCC\n";
        var trustedRepo = createTrustedRepo("github.com", "https", null, null, null, sshKey, sshKnownHosts);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));
        when(manifestGenerator.secretConfig(anyString(), anyMap(), isNull())).thenReturn(mockSecret);

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "git@github.com:user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        GitSecretConfig config = result.get();
        assertThat(config.getSecretName()).isEqualTo("git-secret");
        assertThat(config.getVolumeName()).isEqualTo("git-secret-volume");
        assertThat(config.getSecret()).isEqualTo(mockSecret);
        assertThat(config.getVolumeMounts()).hasSize(2);
        assertThat(config.getVolumeMounts().get(0).getMountPath()).isEqualTo("/root/.ssh/id_rsa");
        assertThat(config.getVolumeMounts().get(0).getSubPath()).isEqualTo("id_rsa");
        assertThat(config.getVolumeMounts().get(0).isReadOnly()).isTrue();
        assertThat(config.getVolumeMounts().get(1).getMountPath()).isEqualTo("/root/.ssh/known_hosts");
        assertThat(config.getVolumeMounts().get(1).getSubPath()).isEqualTo("known_hosts");
        assertThat(config.getVolumeMounts().get(1).isReadOnly()).isTrue();
        assertThat(config.getDefaultMode()).isEqualTo(0600);

        verify(manifestGenerator).secretConfig(eq("git-secret"), anyMap(), isNull());
    }

    @Test
    void prepareGitSecret_shouldThrow_whenSshUrlMatchesButSshKnownHostsNotConfigured() {
        // Given
        var trustedRepo = createTrustedRepo("github.com", "https", null, null, null, "ssh-key", null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When/Then
        assertThatThrownBy(() -> gitService.prepareGitSecret(
                "git@github.com:user/repo.git", "git-secret", manifestGenerator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSH known hosts are not configured for URL: git@github.com:user/repo.git");
    }

    @Test
    void prepareGitSecret_shouldMatchSubdomain_whenSubdomainMatchesTrustedRepo() {
        // Given
        var trustedRepo = createTrustedRepo("github.com", "https", "user", "pass", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://api.github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "git@github.com:user/repo.git",
            "ssh://git@github.com/user/repo.git",
            "ssh://github.com/user/repo.git"
    })
    void prepareGitSecret_shouldHandleDifferentSshUrlFormats(String sshUrl) {
        // Given
        var sshKey = "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----";
        var sshKnownHosts = "github.com ssh-rsa AAABBBCCC\n";
        var trustedRepo = createTrustedRepo("github.com", "https", null, null, null, sshKey, sshKnownHosts);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(sshUrl, "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/user/repo.git",
            "http://github.com/user/repo.git",
            "https://github.com:443/user/repo.git"
    })
    void prepareGitSecret_shouldHandleDifferentHttpsUrlFormats(String httpsUrl) {
        // Given
        var trustedRepo = createTrustedRepo("github.com", "https", "user", "pass", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(httpsUrl, "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
    }

    @Test
    void prepareGitSecret_shouldGenerateCredentialsWithUserAndToken_whenBothProvided() {
        // Given
        var mockSecret = new Secret();
        var trustedRepo = createTrustedRepo("github.com", "https", "testuser", null, "testtoken", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));
        when(manifestGenerator.secretConfig(anyString(), anyMap(), isNull())).thenReturn(mockSecret);

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        verify(manifestGenerator).secretConfig(eq("git-secret"), anyMap(), isNull());
    }

    @Test
    void prepareGitSecret_shouldGenerateCredentialsWithTokenOnly_whenTokenProvidedWithoutUser() {
        // Given
        var mockSecret = new Secret();
        var trustedRepo = createTrustedRepo("github.com", "https", null, null, "testtoken", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));
        when(manifestGenerator.secretConfig(anyString(), anyMap(), isNull())).thenReturn(mockSecret);

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        verify(manifestGenerator).secretConfig(eq("git-secret"), anyMap(), isNull());
    }

    @Test
    void prepareGitSecret_shouldSelectFirstMatchingTrustedRepo_whenMultipleReposMatch() {
        // Given
        var repo1 = createTrustedRepo("github.com", "https", "user1", "pass1", null, null, null);
        var repo2 = createTrustedRepo("github.com", "https", "user2", "pass2", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(repo1, repo2));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
    }

    @Test
    void prepareGitSecret_shouldHandleInvalidUrlGracefully_whenUrlCannotBeParsed() {
        // Given
        var trustedRepo = createTrustedRepo("github.com", "https", "user", "pass", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "not-a-valid-url", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("sshUrlProvider")
    void prepareGitSecret_shouldExtractHostCorrectlyFromSshUrls(String sshUrl, String expectedHost) {
        // Given
        var sshKey = "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----";
        var sshKnownHosts = expectedHost + " ssh-rsa AAABBBCCC\n";
        var trustedRepo = createTrustedRepo(expectedHost, "https", null, null, null, sshKey, sshKnownHosts);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(sshUrl, "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
    }

    static Stream<Arguments> sshUrlProvider() {
        return Stream.of(
                arguments("git@github.com:user/repo.git", "github.com"),
                arguments("ssh://git@github.com/user/repo.git", "github.com"),
                arguments("ssh://github.com/user/repo.git", "github.com"),
                arguments("git@gitlab.com:user/repo.git", "gitlab.com"),
                arguments("ssh://git@gitlab.com/user/repo.git", "gitlab.com")
        );
    }

    @Test
    void prepareGitSecret_shouldUseCorrectProtocol_whenProtocolSpecifiedInTrustedRepo() {
        // Given
        var mockSecret = new Secret();
        var trustedRepo = createTrustedRepo("github.com", "http", "user", "pass", null, null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));
        when(manifestGenerator.secretConfig(anyString(), anyMap(), isNull())).thenReturn(mockSecret);

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isPresent();
        verify(manifestGenerator).secretConfig(eq("git-secret"), anyMap(), isNull());
    }

    @Test
    void prepareGitSecret_shouldNotMatch_whenHttpsUrlButOnlySshCredentialsConfigured() {
        // Given
        var sshKey = "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----";
        var sshKnownHosts = "github.com ssh-rsa AAABBBCCC\n";
        var trustedRepo = createTrustedRepo("github.com", "https", null, null, null, sshKey, sshKnownHosts);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(trustedRepo));

        // When
        Optional<GitSecretConfig> result = gitService.prepareGitSecret(
                "https://github.com/user/repo.git", "git-secret", manifestGenerator);

        // Then
        assertThat(result).isEmpty();
    }

    private GitProperties.TrustedPrivateGitRepo createTrustedRepo(
            String host, String protocol, String user, String password,
            String token, String sshKey, String sshKnownHosts) {
        return createTrustedRepo(host, null, protocol, user, password, token, sshKey, sshKnownHosts);
    }

    private GitProperties.TrustedPrivateGitRepo createTrustedRepo(
            String host, String path, String protocol, String user, String password,
            String token, String sshKey, String sshKnownHosts) {
        var repo = new GitProperties.TrustedPrivateGitRepo();
        repo.setHost(host);
        repo.setPath(path);
        repo.setProtocol(protocol);
        repo.setUser(user);
        repo.setPassword(password);
        repo.setToken(token);
        repo.setSshKey(sshKey);
        repo.setSshKnownHosts(sshKnownHosts);
        return repo;
    }

    /**
     * Resolves credentials for the given URL and returns the generated .git-credentials file content,
     * so tests can assert WHICH configured entry (by its distinguishing token) was selected.
     */
    private String resolveGitCredentialsContent(String gitUrl) {
        return resolveGitSecretData(gitUrl).get(".git-credentials");
    }

    /**
     * Resolves credentials for the given URL and returns the whole generated secret payload, so tests
     * can assert both which entry was selected and which authentication material it contributed.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> resolveGitSecretData(String gitUrl) {
        var mockSecret = new Secret();
        ArgumentCaptor<Map<String, String>> secretDataCaptor = ArgumentCaptor.forClass(Map.class);
        when(manifestGenerator.secretConfig(anyString(), secretDataCaptor.capture(), isNull())).thenReturn(mockSecret);

        Optional<GitSecretConfig> result = gitService.prepareGitSecret(gitUrl, "git-secret", manifestGenerator);

        assertThat(result).isPresent();
        return secretDataCaptor.getValue();
    }

    // ---- User Story 1: repository-specific credentials on a shared domain ----

    @Test
    void findMatchingTrustedRepo_shouldSelectRepositorySpecificEntry_overDomainWideEntry() {
        var domainWide = createTrustedRepo("git.example.com", null, "https", null, null, "domain-token", null, null);
        var repoSpecific = createTrustedRepo("git.example.com", "team/service-a", "https", null, null, "repo-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(domainWide, repoSpecific));

        var credentials = resolveGitCredentialsContent("https://git.example.com/team/service-a.git");

        assertThat(credentials).contains("repo-token").doesNotContain("domain-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldNotApplyRepositorySpecificEntry_toSiblingRepository() {
        var domainWide = createTrustedRepo("git.example.com", null, "https", null, null, "domain-token", null, null);
        var repoSpecific = createTrustedRepo("git.example.com", "team/service-a", "https", null, null, "repo-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(domainWide, repoSpecific));

        var credentials = resolveGitCredentialsContent("https://git.example.com/team/service-b.git");

        assertThat(credentials).contains("domain-token").doesNotContain("repo-token");
    }

    // ---- User Story 2: one token covering every repository in a project ----

    @Test
    void findMatchingTrustedRepo_shouldSelectProjectScopedEntry_forRepositoryUnderItsPath() {
        var projectScoped = createTrustedRepo("git.example.com", "team", "https", null, null, "project-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(projectScoped));

        assertThat(resolveGitCredentialsContent("https://git.example.com/team/service-a.git")).contains("project-token");
        assertThat(resolveGitCredentialsContent("https://git.example.com/team/service-c.git")).contains("project-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldPreferRepositorySpecificEntry_overProjectScopedEntry() {
        var projectScoped = createTrustedRepo("git.example.com", "team", "https", null, null, "project-token", null, null);
        var repoSpecific = createTrustedRepo("git.example.com", "team/service-a", "https", null, null, "repo-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(projectScoped, repoSpecific));

        var credentials = resolveGitCredentialsContent("https://git.example.com/team/service-a.git");

        assertThat(credentials).contains("repo-token").doesNotContain("project-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldMatchProjectScopeOnlyAtSegmentBoundary() {
        var projectScoped = createTrustedRepo("git.example.com", "team", "https", null, null, "project-token", null, null);
        var domainWide = createTrustedRepo("git.example.com", null, "https", null, null, "domain-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(projectScoped, domainWide));

        var credentials = resolveGitCredentialsContent("https://git.example.com/team2/service-a.git");

        assertThat(credentials).contains("domain-token").doesNotContain("project-token");
    }

    // ---- User Story 3: automatic selection of the most specific credential ----

    @Test
    void findMatchingTrustedRepo_shouldResolveAllThreeScopeLevels_regardlessOfConfigurationOrder() {
        var repoSpecific = createTrustedRepo("git.example.com", "team/service-a", "https", null, null, "repo-token", null, null);
        var domainWide = createTrustedRepo("git.example.com", null, "https", null, null, "domain-token", null, null);
        var projectScoped = createTrustedRepo("git.example.com", "team", "https", null, null, "project-token", null, null);
        // Deliberately not in specificity order, to prove resolution doesn't depend on list order.
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(repoSpecific, domainWide, projectScoped));

        assertThat(resolveGitCredentialsContent("https://git.example.com/other-team/x.git")).contains("domain-token");
        assertThat(resolveGitCredentialsContent("https://git.example.com/team/service-b.git")).contains("project-token");
        assertThat(resolveGitCredentialsContent("https://git.example.com/team/service-a.git")).contains("repo-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldPreferExactHostMatch_overSubdomainInheritedMatchWithMoreSpecificPath() {
        var subdomainInheritedRepoSpecific =
                createTrustedRepo("example.com", "team/service-a", "https", null, null, "parent-repo-token", null, null);
        var exactHostProjectScoped =
                createTrustedRepo("sub.example.com", "team", "https", null, null, "exact-project-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(subdomainInheritedRepoSpecific, exactHostProjectScoped));

        var credentials = resolveGitCredentialsContent("https://sub.example.com/team/service-a.git");

        assertThat(credentials).contains("exact-project-token").doesNotContain("parent-repo-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldPreferLongerNestedProjectScope() {
        var groupScoped = createTrustedRepo("gitlab.com", "group", "https", null, null, "outer-scope-token", null, null);
        var subgroupScoped = createTrustedRepo("gitlab.com", "group/subgroup", "https", null, null, "inner-scope-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(groupScoped, subgroupScoped));

        var credentials = resolveGitCredentialsContent("https://gitlab.com/group/subgroup/service.git");

        assertThat(credentials).contains("inner-scope-token").doesNotContain("outer-scope-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldPreferCloserParentDomain_overMorePathSpecificFartherParentDomain() {
        var fartherParentRepoSpecific =
                createTrustedRepo("example.com", "team/service-a", "https", null, null, "farther-parent-token", null, null);
        var closerParentProjectScoped =
                createTrustedRepo("b.example.com", "team", "https", null, null, "closer-parent-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(fartherParentRepoSpecific, closerParentProjectScoped));

        var credentials = resolveGitCredentialsContent("https://a.b.example.com/team/service-a.git");

        assertThat(credentials).contains("closer-parent-token").doesNotContain("farther-parent-token");
    }

    @Test
    void findMatchingTrustedRepo_shouldSelectByAuthType_whenSshAndHttpsEntriesShareTheSameScope() {
        var sshKey = "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----";
        var sshKnownHosts = "git.example.com ssh-rsa AAABBBCCC\n";
        var sshEntry = createTrustedRepo("git.example.com", "team/service-a", null, null, null, null, sshKey, sshKnownHosts);
        var httpsEntry = createTrustedRepo("git.example.com", "team/service-a", "https", null, null, "https-token", null, null);
        when(gitProperties.getTrustedPrivateRepos()).thenReturn(List.of(sshEntry, httpsEntry));

        var httpsSecretData = resolveGitSecretData("https://git.example.com/team/service-a.git");
        var sshSecretData = resolveGitSecretData("git@git.example.com:team/service-a.git");

        assertThat(httpsSecretData).containsKey(".git-credentials").doesNotContainKey("id_rsa");
        assertThat(httpsSecretData.get(".git-credentials")).contains("https-token");
        assertThat(sshSecretData).containsKey("id_rsa").doesNotContainKey(".git-credentials");
        assertThat(sshSecretData.get("id_rsa")).isEqualTo(sshKey);
    }
}

