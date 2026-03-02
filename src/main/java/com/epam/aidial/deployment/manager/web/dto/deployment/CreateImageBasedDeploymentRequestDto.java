package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.validation.ValidDockerImageName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class CreateImageBasedDeploymentRequestDto extends CreateDeploymentRequestDto {

    @Nullable
    private UUID imageDefinitionId;

    @Nullable
    @ValidDockerImageName
    private String imageReference;

    @AssertTrue(message = "Exactly one image source must be provided: imageDefinitionId or imageReference")
    @JsonIgnore
    public boolean isExactlyOneImageSourceProvided() {
        return (imageDefinitionId == null) != (imageReference == null);
    }
}

