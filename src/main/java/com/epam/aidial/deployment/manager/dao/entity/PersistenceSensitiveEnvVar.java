package com.epam.aidial.deployment.manager.dao.entity;

import lombok.Data;

@Data
public class PersistenceSensitiveEnvVar implements PersistenceEnvVar {

    private String name;
    private String k8sSecretName;
    private String k8sSecretKey;
    private PersistenceEnvVarValue value;

    // we don't save secret values to DB
    public void setValue(PersistenceEnvVarValue value) {
        this.value = value;
        if (value == null) {
            return;
        }
        if (value instanceof PersistenceSimpleEnvVarValue simpleValue) {
            simpleValue.setValue(null);
        }
        if (value instanceof PersistenceFileEnvVarValue fileValue) {
            fileValue.setFileContent(null);
        }
    }
}
