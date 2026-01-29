package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.NoPathTraversal;
import com.epam.aidial.deployment.manager.web.validation.NoSurroundingWhitespace;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GitDockerfileImageSourceDto(
        @NotNull String url,
        @Nullable String branchName,
        @Nullable String sha,
        @Nullable @NoSurroundingWhitespace @NoPathTraversal String baseDirectory,
        @Nullable @Size(min = 1) List<@NotNull String> entrypoint
) implements ImageSourceDto {
}
