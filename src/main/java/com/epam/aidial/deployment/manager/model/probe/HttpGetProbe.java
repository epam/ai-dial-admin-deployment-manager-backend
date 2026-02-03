package com.epam.aidial.deployment.manager.model.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpGetProbe implements ProbeHandler {
    private String path;
    @Nullable
    private Integer port;
    @Nullable
    private String portName;
    @Nullable
    private String scheme;
    @Nullable
    private String host;
}
