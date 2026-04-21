package com.epam.aidial.deployment.manager.kubernetes.knative;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KnativeAnnotations {

    // Autoscaling annotations
    public static final String AUTOSCALING_CLASS = "autoscaling.knative.dev/class";
    public static final String AUTOSCALING_METRIC = "autoscaling.knative.dev/metric";
    public static final String AUTOSCALING_TARGET = "autoscaling.knative.dev/target";
    public static final String INITIAL_SCALE = "autoscaling.knative.dev/initial-scale";
    public static final String MIN_SCALE = "autoscaling.knative.dev/min-scale";
    public static final String MAX_SCALE = "autoscaling.knative.dev/max-scale";
    public static final String SCALE_TO_ZERO_RETENTION = "autoscaling.knative.dev/scale-to-zero-pod-retention-period";

    // Autoscaling annotation values
    public static final String AUTOSCALING_CLASS_KPA = "kpa.autoscaling.knative.dev";
    public static final String AUTOSCALING_METRIC_CONCURRENCY = "concurrency";

    // Serving annotations and labels
    public static final String PROGRESS_DEADLINE = "serving.knative.dev/progress-deadline";
    public static final String CREATOR = "serving.knative.dev/creator";
    public static final String SERVICE = "serving.knative.dev/service";
}
