package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import io.fabric8.kubernetes.api.model.Probe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Converts domain probe properties and Kubernetes probes to KServe predictor model StartupProbe.
 */
@Slf4j
@Component
@LogExecution
@RequiredArgsConstructor
public class KserveProbeConverter {

    private final ProbeConverter probeConverter;

    /**
     * Converts domain probe properties to KServe predictor model StartupProbe.
     *
     * @param probeProperties domain probe properties, or null
     * @return KServe StartupProbe or null if probe is null
     */
    @Nullable
    public io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe toKserveStartupProbe(
            @Nullable ProbeProperties probeProperties) {
        var probe = probeConverter.toProbe(probeProperties);
        return toKserveStartupProbe(probe);
    }

    /**
     * Converts fabric8 Probe to KServe predictor model StartupProbe.
     *
     * @param probe fabric8 Probe, or null
     * @return KServe StartupProbe or null if probe is null
     */
    @Nullable
    public io.kserve.serving.v1beta1.inferenceservicespec.predictor.model.StartupProbe toKserveStartupProbe(@Nullable Probe probe) {
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
}
