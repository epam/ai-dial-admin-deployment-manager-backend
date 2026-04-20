package com.epam.aidial.deployment.manager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodInfo {
    private String name;
    private Instant createdAt;
    private int restartCount;
    @Nullable
    private String lastTerminationReason;
    @Nullable
    private Integer lastExitCode;
    @Nullable
    private Integer lastSignal;
    @Nullable
    private Instant lastFinishedAt;
    private List<ContainerDetails> containers;
}
