package com.epam.aidial.deployment.manager.model.probe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TcpSocketProbe implements ProbeHandler {
    private int port;
}
