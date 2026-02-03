package com.epam.aidial.deployment.manager.model.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrpcProbe implements ProbeHandler {
    private Integer port;
    @Nullable
    private String service;
}
