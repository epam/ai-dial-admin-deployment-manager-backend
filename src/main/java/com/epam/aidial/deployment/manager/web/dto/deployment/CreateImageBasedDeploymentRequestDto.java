package com.epam.aidial.deployment.manager.web.dto.deployment;

import com.epam.aidial.deployment.manager.web.dto.ImageTypeDto;
import com.epam.aidial.deployment.manager.web.dto.ScalingDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
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
    private String imageDefinitionName;
    @Nullable
    private String imageDefinitionVersion;
    @Nullable
    private ImageTypeDto imageDefinitionType;
    @Nullable
    @Valid
    private ScalingDto scaling;

    @AssertTrue(message = "Either imageDefinitionId or (imageDefinitionType, imageDefinitionName, imageDefinitionVersion) must be set")
    public boolean isValidImageReference() {
        boolean hasId = imageDefinitionId != null;
        boolean hasTypeNameVersion = imageDefinitionType != null
                && StringUtils.isNotBlank(imageDefinitionName)
                && StringUtils.isNotBlank(imageDefinitionVersion);
        return hasId || hasTypeNameVersion;
    }
}

