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
    /**
     * KServe {@code component} label value of the pod ({@code "predictor"} / {@code "transformer"}),
     * or {@code null} for pods that carry no such label (KNative/raw inference, non-inference types).
     * Lets callers single out the serving pod of a multi-pod InferenceService.
     */
    @Nullable
    private String component;
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
