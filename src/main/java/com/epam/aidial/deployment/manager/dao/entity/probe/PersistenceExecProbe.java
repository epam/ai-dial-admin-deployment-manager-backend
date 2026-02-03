package com.epam.aidial.deployment.manager.dao.entity.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersistenceExecProbe implements PersistenceProbeHandler {
    private List<String> command;
}
