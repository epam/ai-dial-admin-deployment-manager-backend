package com.epam.aidial.deployment.manager.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class GitConfigurationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void gitProperties_shouldReturnEmptyList_whenInputIsBlankNullOrWhitespace(String input) {
        var properties = new GitConfiguration().gitProperties(
                input, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root");

        assertThat(properties.getTrustedPrivateRepos()).isEmpty();
    }

    @Test
    void gitProperties_shouldParseToken_whenUserAndTokenProvided() throws Exception {
        var repo = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo.setHost("github.com");
        repo.setUser("testuser");
        repo.setToken("mySecretToken");
        var json = toJson(List.of(repo));

        var properties = new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root");

        assertThat(properties.getTrustedPrivateRepos()).hasSize(1);
        assertThat(properties.getTrustedPrivateRepos().get(0).getHost()).isEqualTo("github.com");
        assertThat(properties.getTrustedPrivateRepos().get(0).getUser()).isEqualTo("testuser");
        assertThat(properties.getTrustedPrivateRepos().get(0).getToken()).isEqualTo("mySecretToken");
    }

    @Test
    void gitProperties_shouldParsePassword_whenUserAndPasswordProvided() throws Exception {
        var repo = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo.setHost("github.com");
        repo.setUser("testuser");
        repo.setPassword("mySecretPassword");
        var json = toJson(List.of(repo));

        var properties = new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root");

        assertThat(properties.getTrustedPrivateRepos()).hasSize(1);
        assertThat(properties.getTrustedPrivateRepos().get(0).getHost()).isEqualTo("github.com");
        assertThat(properties.getTrustedPrivateRepos().get(0).getUser()).isEqualTo("testuser");
        assertThat(properties.getTrustedPrivateRepos().get(0).getPassword()).isEqualTo("mySecretPassword");
    }

    @Test
    void gitProperties_shouldReadSshKeyAndSshKnownHostsFromFiles_whenFilePathsProvided(@TempDir Path tempDir) throws Exception {
        var sshKeyContent = "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----";
        var sshKnownHostsContent = "github.com ssh-rsa AAABBBCCC\n";

        var sshKeyFile = tempDir.resolve("id_rsa");
        var sshKnownHostsFile = tempDir.resolve("known_hosts");
        Files.writeString(sshKeyFile, sshKeyContent);
        Files.writeString(sshKnownHostsFile, sshKnownHostsContent);

        var repo = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo.setHost("github.com");
        repo.setSshKeyPath(sshKeyFile.toString());
        repo.setSshKnownHostsPath(sshKnownHostsFile.toString());
        var json = toJson(List.of(repo));

        var properties = new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root");

        assertThat(properties.getTrustedPrivateRepos()).hasSize(1);
        assertThat(properties.getTrustedPrivateRepos().get(0).getHost()).isEqualTo("github.com");
        assertThat(properties.getTrustedPrivateRepos().get(0).getSshKey()).isEqualTo(sshKeyContent);
        assertThat(properties.getTrustedPrivateRepos().get(0).getSshKnownHosts()).isEqualTo(sshKnownHostsContent);
    }

    @Test
    void gitProperties_shouldThrow_whenSshKeyFileNotFound(@TempDir Path tempDir) throws Exception {
        var sshKnownHostsFile = tempDir.resolve("known_hosts");
        Files.writeString(sshKnownHostsFile, "github.com ssh-rsa AAABBBCCC\n");
        var nonExistentSshKeyFile = tempDir.resolve("non_existent_key");

        var repo = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo.setHost("github.com");
        repo.setSshKeyPath(nonExistentSshKeyFile.toString());
        repo.setSshKnownHostsPath(sshKnownHostsFile.toString());
        var json = toJson(List.of(repo));

        assertThatThrownBy(() -> new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSH key file not found for host github.com");
    }

    @Test
    void gitProperties_shouldThrow_whenSshKnownHostsFileNotFound(@TempDir Path tempDir) throws Exception {
        var sshKeyFile = tempDir.resolve("id_rsa");
        Files.writeString(sshKeyFile, "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----");
        var nonExistentKnownHostsFile = tempDir.resolve("non_existent_known_hosts");

        var repo = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo.setHost("github.com");
        repo.setSshKeyPath(sshKeyFile.toString());
        repo.setSshKnownHostsPath(nonExistentKnownHostsFile.toString());
        var json = toJson(List.of(repo));

        assertThatThrownBy(() -> new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSH known hosts file not found for host github.com");
    }

    @Test
    void gitProperties_shouldParseMultipleRepos_whenMultipleReposProvided() throws Exception {
        var repo1 = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo1.setHost("github.com");
        repo1.setUser("user1");
        repo1.setPassword("pass1");

        var repo2 = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo2.setHost("gitlab.com");
        repo2.setUser("user2");
        repo2.setToken("token2");

        var json = toJson(List.of(repo1, repo2));

        var properties = new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root");

        assertThat(properties.getTrustedPrivateRepos()).hasSize(2);
        assertThat(properties.getTrustedPrivateRepos().get(0).getHost()).isEqualTo("github.com");
        assertThat(properties.getTrustedPrivateRepos().get(0).getUser()).isEqualTo("user1");
        assertThat(properties.getTrustedPrivateRepos().get(0).getPassword()).isEqualTo("pass1");
        assertThat(properties.getTrustedPrivateRepos().get(1).getHost()).isEqualTo("gitlab.com");
        assertThat(properties.getTrustedPrivateRepos().get(1).getUser()).isEqualTo("user2");
        assertThat(properties.getTrustedPrivateRepos().get(1).getToken()).isEqualTo("token2");
    }

    @Test
    void gitProperties_shouldThrow_whenJsonIsInvalid() {
        var invalidJson = "not a valid json";

        assertThatThrownBy(() -> new GitConfiguration().gitProperties(
                invalidJson, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JSON format for trusted-private-repos");
    }

    @Test
    void gitProperties_shouldThrow_whenNeitherUserNorSshKeyIsSet() {
        var json = "[{\"host\":\"github.com\"}]";

        assertThatThrownBy(() -> new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either user or sshKey must be set for host: github.com");
    }

    @ParameterizedTest
    @MethodSource("sshKeyValidationErrorProvider")
    void gitProperties_shouldThrow_whenSshKeyAndSshKnownHostsNotSetTogether(String json, String expectedMessage) {
        assertThatThrownBy(() -> new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> sshKeyValidationErrorProvider() throws Exception {
        var repo1 = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo1.setHost("github.com");
        repo1.setSshKeyPath("/path/to/ssh-key");

        var repo2 = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo2.setHost("github.com");
        repo2.setSshKnownHostsPath("/path/to/known-hosts");

        return Stream.of(
                arguments(toJson(List.of(repo1)),
                        "sshKey and sshKnownHosts must be set at the same time"),
                arguments(toJson(List.of(repo2)),
                        "Either user or sshKey must be set for host: github.com")
        );
    }

    @ParameterizedTest
    @MethodSource("userPasswordValidationErrorProvider")
    void gitProperties_shouldThrow_whenUserPasswordValidationFails(String json, String expectedMessage) {
        assertThatThrownBy(() -> new GitConfiguration().gitProperties(
                json, ".git-credentials", ".gitconfig", "id_rsa", "known_hosts", "git-secret-volume", "/root"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> userPasswordValidationErrorProvider() throws Exception {
        var repo1 = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo1.setHost("github.com");
        repo1.setUser("testuser");

        var repo2 = new GitPropertiesDto.TrustedPrivateGitRepoDto();
        repo2.setHost("github.com");
        repo2.setPassword("password");

        return Stream.of(
                arguments(toJson(List.of(repo1)),
                        "If user is set, then either password or token must be set"),
                arguments(toJson(List.of(repo2)),
                        "Either user or sshKey must be set for host: github.com")
        );
    }

    private static String toJson(List<GitPropertiesDto.TrustedPrivateGitRepoDto> repos) throws Exception {
        return MAPPER.writeValueAsString(repos);
    }

}
