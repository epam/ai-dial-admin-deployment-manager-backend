package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodInfo {
    private String name;
    @Nullable
    private String component;
    @Nullable
    private String mainContainerName;
    private Instant createdAt;
    private int restartCount;
    @Nullable
    private String lastTerminationReason;
    @Nullable
    private String lastTerminationMessage;
    @Nullable
    private Integer lastExitCode;
    @Nullable
    private Integer lastSignal;
    @Nullable
    private Instant lastFinishedAt;
}
