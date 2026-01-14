package com.epam.aidial.deployment.manager.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PersistenceFileEnvVarValue implements PersistenceEnvVarValue {
    private String fileName;
    private String fileContent;
}