package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.EngineFamily;
import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.model.metrics.ParsedExposition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EngineDetectorTest {

    private final EngineDetector detector = new EngineDetector();

    @Test
    void shouldDetectVllmByMetricPrefix() {
        var exposition = withSamples("python_gc_objects_collected_total", "vllm:num_requests_running");

        assertThat(detector.detect(exposition)).isEqualTo(EngineFamily.VLLM);
    }

    @Test
    void shouldDetectTgiByMetricPrefix() {
        var exposition = withSamples("tgi_queue_size", "process_start_time_seconds");

        assertThat(detector.detect(exposition)).isEqualTo(EngineFamily.TGI);
    }

    @Test
    void shouldDetectSglangByMetricPrefix() {
        var exposition = withSamples("sglang:num_running_reqs");

        assertThat(detector.detect(exposition)).isEqualTo(EngineFamily.SGLANG);
    }

    @Test
    void shouldDetectKserveModelServerByDeclaredName_whenIdleWithNoSamples() {
        // An idle KServe Python ModelServer emits no request_* samples — only their # TYPE metadata
        var exposition = new ParsedExposition(List.of(), Set.of("request_predict_seconds", "request_preprocess_seconds"));

        assertThat(detector.detect(exposition)).isEqualTo(EngineFamily.KSERVE_MODELSERVER);
    }

    @Test
    void shouldDetectKserveModelServerByDeclaredName_whenSamplesPresent() {
        var exposition = new ParsedExposition(
                samples("request_predict_seconds_count", "process_cpu_seconds_total"),
                Set.of("request_predict_seconds", "process_cpu_seconds_total"));

        assertThat(detector.detect(exposition)).isEqualTo(EngineFamily.KSERVE_MODELSERVER);
    }

    @Test
    void shouldFallBackToUnknown_whenNoDistinctiveSignalFound() {
        assertThat(detector.detect(new ParsedExposition(List.of(), Set.of()))).isEqualTo(EngineFamily.UNKNOWN);
        assertThat(detector.detect(new ParsedExposition(
                samples("process_start_time_seconds", "http_requests_total"),
                Set.of("process_start_time_seconds", "http_requests_total"))))
                .isEqualTo(EngineFamily.UNKNOWN);
    }

    private static ParsedExposition withSamples(String... names) {
        return new ParsedExposition(samples(names), Set.of(names));
    }

    private static List<MetricSample> samples(String... names) {
        return Arrays.stream(names)
                .map(name -> new MetricSample(name, Map.of(), 1.0))
                .toList();
    }

}
