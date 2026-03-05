package com.epam.aidial.deployment.manager.model.deployment;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class InferenceDeployment extends Deployment {
    private String modelFormat;
    private InferenceDeploymentSource source;
    @Nullable
    private List<String> command;
    @Nullable
    private List<String> args;
}
