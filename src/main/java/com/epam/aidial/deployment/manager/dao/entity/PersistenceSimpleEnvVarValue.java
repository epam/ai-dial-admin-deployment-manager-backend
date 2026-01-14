package com.epam.aidial.deployment.manager.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PersistenceSimpleEnvVarValue implements PersistenceEnvVarValue {
    private String value;
}