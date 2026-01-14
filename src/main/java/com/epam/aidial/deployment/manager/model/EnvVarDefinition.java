package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvVarDefinition {
    private String name;
    private EnvVarValue value;
    private EnvVarMountType mountType;
    private String description;
}
