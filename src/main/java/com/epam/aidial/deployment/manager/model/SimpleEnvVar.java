package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleEnvVar implements EnvVar {

    private String name;
    private EnvVarValue value;

}
