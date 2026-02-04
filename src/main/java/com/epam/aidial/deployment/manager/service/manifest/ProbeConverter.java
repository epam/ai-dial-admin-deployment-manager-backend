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
 * Converts domain probe properties to Kubernetes probe and to KServe/NIM startup probe types.
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
        if (properties == null) {
            log.debug("Probe properties are null, skipping probe conversion");
            return null;
        }
        if (!properties.isEnabled()) {
            log.debug("Probe properties are disabled, skipping probe conversion");
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
                    .httpPort(portFrom(h.getPort()))
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
            log.warn("Probe must have httpGet handler with path and port; probe not built");
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

    private static IntOrString portFrom(@Nullable Integer port) {
        return port != null ? new IntOrString(port) : null;
    }

    @Builder
    private record HandlerParams(
            String handlerType,
            IntOrString httpPort,
            String httpPath
    ) { }

    /**
     * Converts domain probe properties to KServe predictor model StartupProbe.
     *
     * @param probeProperties domain probe properties, or null
     * @return KServe StartupProbe or null if probe is null
     */
    @Nullable
    public io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe toKserveStartupProbe(
            @Nullable ProbeProperties probeProperties) {
        var probe = toProbe(probeProperties);
        return toKserveStartupProbe(probe);
    }

    /**
     * Converts fabric8 Probe to KServe predictor model StartupProbe.
     *
     * @param probe fabric8 Probe, or null
     * @return KServe StartupProbe or null if probe is null
     */
    @Nullable
    private io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe toKserveStartupProbe(
            @Nullable Probe probe) {
        if (probe == null) {
            log.debug("Probe is null, skipping KServe startup probe conversion");
            return null;
        }
        var kserve = new io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe();
        copyTimingToKserve(probe, kserve);
        copyHandlerToKserve(probe, kserve);
        log.debug("Converted fabric8 Probe to KServe StartupProbe");
        return kserve;
    }

    private static void copyTimingToKserve(Probe source,
            io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe target) {
        if (source.getInitialDelaySeconds() != null) {
            target.setInitialDelaySeconds(source.getInitialDelaySeconds());
        }
        if (source.getPeriodSeconds() != null) {
            target.setPeriodSeconds(source.getPeriodSeconds());
        }
        if (source.getTimeoutSeconds() != null) {
            target.setTimeoutSeconds(source.getTimeoutSeconds());
        }
        if (source.getFailureThreshold() != null) {
            target.setFailureThreshold(source.getFailureThreshold());
        }
        if (source.getSuccessThreshold() != null) {
            target.setSuccessThreshold(source.getSuccessThreshold());
        }
        if (source.getTerminationGracePeriodSeconds() != null) {
            target.setTerminationGracePeriodSeconds(source.getTerminationGracePeriodSeconds());
        }
    }

    private static void copyHandlerToKserve(Probe source,
            io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe target) {
        if (source.getHttpGet() != null) {
            var httpGet = new io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.startupprobe.HttpGet();
            httpGet.setPath(source.getHttpGet().getPath());
            httpGet.setPort(source.getHttpGet().getPort());
            target.setHttpGet(httpGet);
        }
    }

    /**
     * Converts domain probe properties to NIM StartupProbe (with enabled set to true and probe payload).
     *
     * @param probeProperties domain probe properties, or null
     * @return NIM StartupProbe or null if probe is null
     */
    @Nullable
    public com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe toNimStartupProbe(
            @Nullable ProbeProperties probeProperties) {
        var probe = toProbe(probeProperties);
        return toNimStartupProbe(probe);
    }

    /**
     * Converts fabric8 Probe to NIM StartupProbe (with enabled set to true and probe payload).
     *
     * @param probe fabric8 Probe, or null
     * @return NIM StartupProbe or null if probe is null
     */
    @Nullable
    private com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe toNimStartupProbe(@Nullable Probe probe) {
        if (probe == null) {
            log.debug("Probe is null, skipping NIM startup probe conversion");
            return null;
        }
        var nimStartupProbe = new com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe();
        nimStartupProbe.setEnabled(true);
        var nimProbe = new com.nvidia.apps.v1alpha1.nimservicespec.startupprobe.Probe();
        copyTimingToNimProbe(probe, nimProbe);
        copyHandlerToNimProbe(probe, nimProbe);
        nimStartupProbe.setProbe(nimProbe);
        log.debug("Converted fabric8 Probe to NIM StartupProbe");
        return nimStartupProbe;
    }

    private static void copyTimingToNimProbe(Probe source,
            com.nvidia.apps.v1alpha1.nimservicespec.startupprobe.Probe target) {
        if (source.getInitialDelaySeconds() != null) {
            target.setInitialDelaySeconds(source.getInitialDelaySeconds());
        }
        if (source.getPeriodSeconds() != null) {
            target.setPeriodSeconds(source.getPeriodSeconds());
        }
        if (source.getTimeoutSeconds() != null) {
            target.setTimeoutSeconds(source.getTimeoutSeconds());
        }
        if (source.getFailureThreshold() != null) {
            target.setFailureThreshold(source.getFailureThreshold());
        }
        if (source.getSuccessThreshold() != null) {
            target.setSuccessThreshold(source.getSuccessThreshold());
        }
        if (source.getTerminationGracePeriodSeconds() != null) {
            target.setTerminationGracePeriodSeconds(source.getTerminationGracePeriodSeconds());
        }
    }

    private static void copyHandlerToNimProbe(Probe source,
            com.nvidia.apps.v1alpha1.nimservicespec.startupprobe.Probe target) {
        if (source.getHttpGet() != null) {
            var httpGet = new com.nvidia.apps.v1alpha1.nimservicespec.startupprobe.probe.HttpGet();
            httpGet.setPath(source.getHttpGet().getPath());
            httpGet.setPort(source.getHttpGet().getPort());
            target.setHttpGet(httpGet);
        }
    }
}
