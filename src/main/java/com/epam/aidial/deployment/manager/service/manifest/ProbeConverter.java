package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeHandler;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converts domain probe properties to Kubernetes probe.
 */
@Slf4j
@Component
@LogExecution
public class ProbeConverter {

    /**
     * Converts domain probe properties to Kubernetes probe. Returns null if properties are null
     * or not enabled.
     */
    @Nullable
    public Probe toProbe(@Nullable ProbeProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            log.debug("Probe properties are disabled or null, skipping probe conversion");
            return null;
        }
        ProbeHandler handler = properties.getProbe();
        HandlerParams params = extractHandlerParams(handler);
        Probe probe = buildProbe(
                properties.getInitialDelaySeconds(),
                properties.getPeriodSeconds(),
                properties.getTimeoutSeconds(),
                properties.getFailureThreshold(),
                params);
        if (probe != null) {
            log.debug("Converted probe properties to Kubernetes Probe (handler type: {})",
                    params.handlerType());
        }
        return probe;
    }

    private static HandlerParams extractHandlerParams(@Nullable ProbeHandler handler) {
        if (handler instanceof HttpGetProbe h) {
            return HandlerParams.builder()
                    .handlerType("httpGet")
                    .httpPort(new IntOrString(h.getPort()))
                    .httpPath(h.getPath())
                    .build();
        }
        return HandlerParams.builder().handlerType("unknown").build();
    }

    private static Probe buildProbe(
            @Nullable Integer initialDelaySeconds,
            @Nullable Integer periodSeconds,
            @Nullable Integer timeoutSeconds,
            @Nullable Integer failureThreshold,
            HandlerParams params) {
        if (params.httpPath() == null || params.httpPort() == null) {
            log.warn("Probe must have httpGet handler with path and port. Probe not built.");
            return null;
        }
        var probe = new Probe();
        setTimingOnProbe(probe, initialDelaySeconds, periodSeconds, timeoutSeconds, failureThreshold);
        var httpGet = new HTTPGetAction();
        httpGet.setPath(params.httpPath());
        httpGet.setPort(params.httpPort());
        probe.setHttpGet(httpGet);
        return probe;
    }

    /**
     * Sets timing-related fields on a Kubernetes Probe. Fields that are null are omitted from the
     * probe, and Kubernetes will apply default values for them.
     *
     * @param probe the Kubernetes Probe to modify
     * @param initialDelaySeconds seconds after container start before the probe is initiated (optional)
     * @param periodSeconds how often to perform the probe (optional)
     * @param timeoutSeconds seconds after which the probe attempt times out (optional)
     * @param failureThreshold minimum consecutive failures for the probe to be considered failed (optional)
     */
    private static void setTimingOnProbe(Probe probe,
            @Nullable Integer initialDelaySeconds,
            @Nullable Integer periodSeconds,
            @Nullable Integer timeoutSeconds,
            @Nullable Integer failureThreshold) {
        if (initialDelaySeconds != null) {
            probe.setInitialDelaySeconds(initialDelaySeconds);
        }
        if (periodSeconds != null) {
            probe.setPeriodSeconds(periodSeconds);
        }
        if (timeoutSeconds != null) {
            probe.setTimeoutSeconds(timeoutSeconds);
        }
        if (failureThreshold != null) {
            probe.setFailureThreshold(failureThreshold);
        }
    }

    @Builder
    private record HandlerParams(
            String handlerType,
            @Nullable IntOrString httpPort,
            @Nullable String httpPath
    ) { }
}
