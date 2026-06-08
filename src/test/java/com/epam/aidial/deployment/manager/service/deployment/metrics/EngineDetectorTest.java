package com.epam.aidial.deployment.manager.service.deployment.metrics;

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

        assertThat(detector.detect(samples)).isEqualTo(EngineFamily.VLLM);
    }

    @Test
    void shouldDetectTgiByMetricPrefix() {
        var samples = samples("tgi_queue_size", "process_start_time_seconds");

        assertThat(detector.detect(samples)).isEqualTo(EngineFamily.TGI);
    }

    @Test
    void shouldDetectSglangByMetricPrefix() {
        var samples = samples("sglang:num_running_reqs");

        assertThat(detector.detect(samples)).isEqualTo(EngineFamily.SGLANG);
    }

    @Test
    void shouldFallBackToUnknown_whenNoDistinctivePrefixFound() {
        assertThat(detector.detect(List.of())).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(detector.detect(samples("process_start_time_seconds", "http_requests_total")))
                .isEqualTo(EngineFamily.UNKNOWN);
    }

    private static List<MetricSample> samples(String... names) {
        return Arrays.stream(names)
                .map(name -> new MetricSample(name, Map.of(), 1.0))
                .toList();
    }

}
