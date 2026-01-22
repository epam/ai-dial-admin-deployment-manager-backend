package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodInfo {
    private String name;
    private Instant createdAt;
    private int restartCount;
    private String lastTerminationReason;
    private Integer lastExitCode;
    private Integer lastSignal;
}
