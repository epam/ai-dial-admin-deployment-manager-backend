package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveEnvVar implements EnvVar {

    private String name;
    private EnvVarValue value;
    private String k8sSecretName;
    private String k8sSecretKey;
}
