package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitCommandBuilderTest {

    private static final String TEST_URL = "https://github.com/user/repo.git";
    private static final String WORKSPACE_PATH = "/workspace";

    @Test
    void buildGitCloneCommand_shouldUseBranchStrategy_whenBranchNameIsProvided() {
        // Given
        var source = GitDockerfileImageSource.builder()
                .url(TEST_URL)
                .branchName("main")
                .build();

        // When
        String command = GitCommandBuilder.buildGitCloneCommand(source);

        // Then
        assertThat(command).isEqualTo("git clone --depth 1 --branch main %s %s".formatted(TEST_URL, WORKSPACE_PATH));
    }

    @Test
    void buildGitCloneCommand_shouldUseShaStrategy_whenShaIsProvided() {
        // Given
        var source = GitDockerfileImageSource.builder()
                .url(TEST_URL)
                .sha("abc123def456789")
                .build();

        // When
        String command = GitCommandBuilder.buildGitCloneCommand(source);

        // Then
        assertThat(command)
                .isEqualTo("git clone --depth 1 %s %s && git -C %s fetch origin %s && git -C %s checkout FETCH_HEAD"
                        .formatted(TEST_URL, WORKSPACE_PATH, WORKSPACE_PATH, "abc123def456789", WORKSPACE_PATH));
    }

    @Test
    void buildGitCloneCommand_shouldUseDefaultStrategy_whenBothBranchNameAndShaAreBlank() {
        // Given
        var source = GitDockerfileImageSource.builder()
                .url(TEST_URL)
                .build();

        // When
        String command = GitCommandBuilder.buildGitCloneCommand(source);

        // Then
        assertThat(command).isEqualTo("git clone --depth 1 %s %s".formatted(TEST_URL, WORKSPACE_PATH));
    }

    @Test
    void buildGitCloneCommand_shouldThrowException_whenBothBranchNameAndShaAreProvided() {
        // Given
        var source = GitDockerfileImageSource.builder()
                .url(TEST_URL)
                .branchName("main")
                .sha("abc123def456")
                .build();

        // When/Then
        assertThatThrownBy(() -> GitCommandBuilder.buildGitCloneCommand(source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot specify both branchName and sha");
    }

}
