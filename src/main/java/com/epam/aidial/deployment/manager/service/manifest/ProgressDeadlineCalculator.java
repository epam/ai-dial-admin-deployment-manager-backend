package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@LogExecution
public class ProgressDeadlineCalculator {

    private static final int DEFAULT_TIMEOUT = 1;

    private final int defaultInitialDelay;
    private final int defaultPeriod;
    private final int defaultFailureThreshold;
    private final int bufferSeconds;

    public ProgressDeadlineCalculator(
            @Value("${app.deployment.progress-deadline.default-initial-delay}") int defaultInitialDelay,
            @Value("${app.deployment.progress-deadline.default-period}") int defaultPeriod,
            @Value("${app.deployment.progress-deadline.default-failure-threshold}") int defaultFailureThreshold,
            @Value("${app.deployment.progress-deadline.buffer-seconds}") int bufferSeconds
    ) {
        this.defaultInitialDelay = defaultInitialDelay;
        this.defaultPeriod = defaultPeriod;
        this.defaultFailureThreshold = defaultFailureThreshold;
        this.bufferSeconds = bufferSeconds;
    }

    /**
     * Returns progress deadline duration string (e.g. "1800s"),
     * or null if no probe is configured.
     */
    @Nullable
    public String compute(@Nullable ProbeProperties probe) {
        if (probe == null || !probe.isEnabled()) {
            return null;
        }
        return computeFromProbe(probe);
    }

    /**
     * Returns progress deadline duration string (e.g. "1800s").
     * When a probe is configured, computes the deadline from probe parameters.
     * When no probe is configured, falls back to {@code fallbackTimeoutSeconds + bufferSeconds}.
     */
    public String compute(@Nullable ProbeProperties probe, int fallbackTimeoutSeconds) {
        if (probe == null || !probe.isEnabled()) {
            int deadline = fallbackTimeoutSeconds + bufferSeconds;
            log.debug("No probe configured, using fallback progress deadline: {}s (fallback={}, buffer={})",
                    deadline, fallbackTimeoutSeconds, bufferSeconds);
            return deadline + "s";
        }
        return computeFromProbe(probe);
    }

    private String computeFromProbe(ProbeProperties probe) {
        int initial = probe.getInitialDelaySeconds() != null
                ? probe.getInitialDelaySeconds() : defaultInitialDelay;
        int period = probe.getPeriodSeconds() != null
                ? probe.getPeriodSeconds() : defaultPeriod;
        int threshold = probe.getFailureThreshold() != null
                ? probe.getFailureThreshold() : defaultFailureThreshold;
        int timeout = probe.getTimeoutSeconds() != null
                ? probe.getTimeoutSeconds() : DEFAULT_TIMEOUT;
        int deadline = initial + ((threshold - 1) * period) + timeout + bufferSeconds;
        log.debug("Computed progress deadline: {}s (initial={}, period={}, threshold={}, timeout={}, buffer={})",
                deadline, initial, period, threshold, timeout, bufferSeconds);
        return deadline + "s";
    }
}
