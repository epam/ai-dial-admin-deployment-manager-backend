package com.epam.aidial.deployment.manager.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PersistenceSimpleEnvVar implements PersistenceEnvVar {

    private String name;
    private PersistenceEnvVarValue value;

}
