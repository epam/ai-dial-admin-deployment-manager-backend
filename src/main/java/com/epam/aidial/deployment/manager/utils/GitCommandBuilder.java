package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.model.GitDockerfileImageSource;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility class for building git clone commands.
 */
public class GitCommandBuilder {

    private static final String WORKSPACE_PATH = "/workspace";

    public static String buildGitCloneCommand(GitDockerfileImageSource source) {
        var url = source.getUrl();
        var branch = source.getBranchName();
        var sha = source.getSha();
        var path = WORKSPACE_PATH;

        // Validate that both branch and sha are not provided simultaneously
        if (StringUtils.isNotBlank(branch) && StringUtils.isNotBlank(sha)) {
            throw new IllegalArgumentException(
                    "Cannot specify both branchName and sha. Please provide either branchName or sha, but not both.");
        }

        // 1. Branch Strategy: Clone directly (Simplest/Fastest)
        if (StringUtils.isNotBlank(branch)) {
            return "git clone --depth 1 --branch %s %s %s".formatted(branch, url, path);
        }

        // 2. SHA Strategy: Clone default -> Fetch specific commit -> Checkout
        if (StringUtils.isNotBlank(sha)) {
            return "git clone --depth 1 %s %s && git -C %s fetch origin %s && git -C %s checkout FETCH_HEAD"
                    .formatted(url, path, path, sha, path);
        }

        // 3. Default Strategy: Just clone HEAD
        return "git clone --depth 1 %s %s".formatted(url, path);
    }

}
