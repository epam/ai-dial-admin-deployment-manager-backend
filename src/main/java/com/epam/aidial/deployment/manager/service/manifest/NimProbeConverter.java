package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import io.fabric8.kubernetes.api.model.Probe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converts domain probe properties and Kubernetes probes to NIM StartupProbe.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class NimProbeConverter {

    private final ProbeConverter probeConverter;

    /**
     * Converts domain probe properties to NIM StartupProbe (with enabled set to true and probe payload).
     *
     * @param probeProperties domain probe properties, or null
     * @return NIM StartupProbe or null if probe is null
     */
    @Nullable
    public com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe toNimStartupProbe(
            @Nullable ProbeProperties probeProperties) {
        var probe = probeConverter.toProbe(probeProperties);
        return toNimStartupProbe(probe);
    }

    /**
     * Converts fabric8 Probe to NIM StartupProbe (with enabled set to true and probe payload).
     *
     * @param probe fabric8 Probe, or null
     * @return NIM StartupProbe or null if probe is null
     */
    @Nullable
    public com.nvidia.apps.v1alpha1.nimservicespec.StartupProbe toNimStartupProbe(@Nullable Probe probe) {
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
