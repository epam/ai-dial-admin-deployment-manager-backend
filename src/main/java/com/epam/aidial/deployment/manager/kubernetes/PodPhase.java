package com.epam.aidial.deployment.manager.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import lombok.Getter;
import org.apache.commons.lang3.EnumUtils;

import java.util.Optional;

@Getter
public enum PodPhase {

    PENDING("Pending", false),
    RUNNING("Running", false),
    SUCCEEDED("Succeeded", true),
    FAILED("Failed", true),
    UNKNOWN("Unknown", false);

    private final String value;
    private final boolean isFinal;

    PodPhase(String value, boolean isFinal) {
        this.value = value;
        this.isFinal = isFinal;
    }

    public static PodPhase fromPodStrictly(Pod pod) {
        return fromPod(pod)
                .orElseThrow(() -> new IllegalArgumentException("Cannot get PodState from pod: " + pod));
    }

    public static Optional<PodPhase> fromPod(Pod pod) {
        final PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return Optional.empty();
        }
        final String podPhase = podStatus.getPhase();
        if (podPhase == null) {
            return Optional.empty();
        }
        var podPhaseParsed = EnumUtils.getEnumIgnoreCase(PodPhase.class, podPhase);
        return Optional.ofNullable(podPhaseParsed);
    }

}
