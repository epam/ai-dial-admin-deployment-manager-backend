package com.epam.aidial.deployment.manager.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record GitDockerfileImageSourceDto(
        @NotNull String url,
        @Nullable String branchName,
        @Nullable String sha,
        @Nullable @Pattern(regexp = "^[^/].*[^/]$|^[^/]+$", message = "Path must not start or end with '/'") String baseDirectory,
        @Nullable @Size(min = 1) List<@NotNull String> entrypoint
) implements ImageSourceDto {
}
