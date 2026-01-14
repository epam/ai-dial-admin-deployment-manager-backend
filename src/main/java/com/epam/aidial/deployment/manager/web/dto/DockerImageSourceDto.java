package com.epam.aidial.deployment.manager.web.dto;

import com.epam.aidial.deployment.manager.web.validation.ValidDockerImageName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record DockerImageSourceDto(
        @NotNull @ValidDockerImageName String imageUri,
        @Nullable @Size(min = 1) List<@NotNull String> entrypoint
) implements ImageSourceDto {
}
