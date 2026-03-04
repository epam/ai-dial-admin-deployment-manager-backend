package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import org.jetbrains.annotations.Nullable;

public class ProgressDeadlineCalculator {

    private static final int DEFAULT_INITIAL_DELAY = 0;
    private static final int DEFAULT_PERIOD = 10;
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final int BUFFER_SECONDS = 30;

    private ProgressDeadlineCalculator() {
    }

    /**
     * Returns progress deadline duration string (e.g. "1800s"),
     * or null if no probe is configured.
     */
    @Nullable
    public static String compute(@Nullable ProbeProperties probe) {
        if (probe == null || !probe.isEnabled()) {
            return null;
        }
        int initial = probe.getInitialDelaySeconds() != null
                ? probe.getInitialDelaySeconds() : DEFAULT_INITIAL_DELAY;
        int period = probe.getPeriodSeconds() != null
                ? probe.getPeriodSeconds() : DEFAULT_PERIOD;
        int threshold = probe.getFailureThreshold() != null
                ? probe.getFailureThreshold() : DEFAULT_FAILURE_THRESHOLD;
        int deadline = initial + (period * threshold) + BUFFER_SECONDS;
        return deadline + "s";
    }
}
