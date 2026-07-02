package com.epam.aidial.deployment.manager.web.dto.internal;

import com.epam.aidial.deployment.manager.model.deployment.InferenceTask;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InferenceDeploymentInternalDto extends DeploymentInternalDto {
    @NotNull
    private InferenceTask inferenceTask;
}
