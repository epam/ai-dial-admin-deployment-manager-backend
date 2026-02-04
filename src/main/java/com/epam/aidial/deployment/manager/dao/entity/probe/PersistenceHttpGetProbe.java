package com.epam.aidial.deployment.manager.dao.entity.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceHttpGetProbe implements PersistenceProbeHandler {
    private String path;
    private Integer port;
}
