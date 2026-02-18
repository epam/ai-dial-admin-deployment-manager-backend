package com.epam.aidial.deployment.manager.model.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ExportRequest {
    private boolean addSecrets;
    private boolean addGlobalImageBuildDomainWhitelist;
}
