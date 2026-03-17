package com.epam.aidial.deployment.manager.dao.entity;

import java.util.List;

public record PersistenceGitDockerfileImageSource(
        String url,
        String branchName,
        String sha,
        String baseDirectory,
        List<String> entrypoint,
        PersistenceExternalRegistryRef externalRegistryRef
) implements PersistenceImageSource {
}
