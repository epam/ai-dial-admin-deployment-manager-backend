package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.deployment.InferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.NimDeployment;
import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EngineDetectorTest {

    private final EngineDetector detector = new EngineDetector();

    @Test
    void shouldDetectVllmByMetricPrefix() {
        var samples = samples("python_gc_objects_collected_total", "vllm:num_requests_running");

        assertThat(detector.detect(inference(), samples)).isEqualTo(EngineFamily.VLLM);
    }

    @Test
    void shouldDetectTgiByMetricPrefix() {
        var samples = samples("tgi_queue_size", "process_start_time_seconds");

        assertThat(detector.detect(inference(), samples)).isEqualTo(EngineFamily.TGI);
    }

    @Test
    void shouldDetectSglangByMetricPrefix() {
        var samples = samples("sglang:num_running_reqs");

        assertThat(detector.detect(inference(), samples)).isEqualTo(EngineFamily.SGLANG);
    }

    @Test
    void shouldDetectNimByDeploymentType() {
        // NIM is identified by type even before any samples exist
        assertThat(detector.detect(NimDeployment.builder().build(), List.of())).isEqualTo(EngineFamily.NIM);
        assertThat(detector.detect(NimDeployment.builder().build(), samples("nv_inference_count")))
                .isEqualTo(EngineFamily.NIM);
    }

    @Test
    void shouldFallBackToUnknown_whenNoDistinctivePrefixFound() {
        assertThat(detector.detect(inference(), List.of())).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(detector.detect(inference(), samples("process_start_time_seconds", "http_requests_total")))
                .isEqualTo(EngineFamily.UNKNOWN);
    }

    private static InferenceDeployment inference() {
        return InferenceDeployment.builder().build();
    }

    private static List<MetricSample> samples(String... names) {
        return Arrays.stream(names)
                .map(name -> new MetricSample(name, Map.of(), 1.0))
                .toList();
    }

}
