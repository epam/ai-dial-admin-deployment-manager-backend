package com.epam.aidial.deployment.manager.dao.entity.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceGrpcProbe implements PersistenceProbeHandler {
    private Integer port;
    private String service;
}
