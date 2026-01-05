package com.epam.aidial.deployment.manager.model.deployment;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CreateInferenceDeployment extends CreateDeployment {
    private String modelFormat;
    private InferenceDeploymentSource source;
    private List<String> command;
    private List<String> args;
}
