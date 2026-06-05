package com.epam.aidial.deployment.manager.service.deployment.metrics;

import com.epam.aidial.deployment.manager.model.metrics.MetricSample;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusTextParserTest {

    private final PrometheusTextParser parser = new PrometheusTextParser();

    @Test
    void shouldParseVllmFixture() {
        var samples = parser.parse(ResourceUtils.readResource("/metrics-fixtures/vllm-v0.txt"));

        assertThat(samples).isNotEmpty();
        assertThat(findOne(samples, "vllm:num_requests_running").value()).isEqualTo(1.0);
        assertThat(findOne(samples, "vllm:gpu_cache_usage_perc").value()).isEqualTo(0.27);
        assertThat(findOne(samples, "vllm:num_requests_running").labels())
                .containsEntry("model_name", "meta-llama/Llama-3.1-8B-Instruct");

        // Three label sets of the same counter survive as distinct samples
        var successSamples = findAll(samples, "vllm:request_success_total");
        assertThat(successSamples).hasSize(3);
        assertThat(successSamples).anyMatch(s -> "abort".equals(s.labels().get("finished_reason")) && s.value() == 4.0);
    }

    @Test
    void shouldParseRealVllmV1Capture() {
        // vllm.txt is a real dev-cluster vLLM V1 capture (KServe pod) — spike §3 verification
        var samples = parser.parse(ResourceUtils.readResource("/metrics-fixtures/vllm.txt"));

        assertThat(samples).isNotEmpty();
        var kvCache = findOne(samples, "vllm:kv_cache_usage_perc");
        assertThat(kvCache.labels())
                .containsEntry("engine", "0")
                .containsEntry("model_name", "/mnt/models");

        // V1 exposes five finished_reason label sets (stop/length/abort/error/repetition)
        assertThat(findAll(samples, "vllm:request_success_total")).hasSize(5);

        // scientific notation in a real sample
        assertThat(findOne(samples, "process_start_time_seconds").value()).isEqualTo(1.78059376081e9);
    }

    @Test
    void shouldParseInfNanAndScientificNotation() {
        var samples = parser.parse(ResourceUtils.readResource("/metrics-fixtures/vllm-v0.txt"));

        var infBucket = findAll(samples, "vllm:time_to_first_token_seconds_bucket").stream()
                .filter(s -> "+Inf".equals(s.labels().get("le")))
                .findFirst()
                .orElseThrow();
        assertThat(infBucket.value()).isEqualTo(200.0);

        assertThat(findOne(samples, "vllm:avg_prompt_throughput_toks_per_s").value()).isNaN();
        assertThat(findOne(samples, "process_start_time_seconds").value()).isEqualTo(1.7e9);
        assertThat(findOne(samples, "vllm:gpu_cache_usage_perc").value()).isEqualTo(0.27);

        var infValues = parser.parse("pos_inf +Inf\nneg_inf -Inf\n");
        assertThat(findOne(infValues, "pos_inf").value()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(findOne(infValues, "neg_inf").value()).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void shouldParseHistogramSeries() {
        var samples = parser.parse(ResourceUtils.readResource("/metrics-fixtures/tgi.txt"));

        assertThat(findAll(samples, "tgi_request_duration_bucket")).hasSize(4);
        assertThat(findOne(samples, "tgi_request_duration_sum").value()).isEqualTo(90.0);
        assertThat(findOne(samples, "tgi_request_duration_count").value()).isEqualTo(100.0);
    }

    @Test
    void shouldParseSampleWithoutLabels() {
        var samples = parser.parse("my_metric 42.5 1700000000000\n");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().name()).isEqualTo("my_metric");
        assertThat(samples.getFirst().labels()).isEmpty();
        assertThat(samples.getFirst().value()).isEqualTo(42.5);
    }

    @Test
    void shouldParseEscapedLabelValues() {
        var samples = parser.parse("m{path="
                + "\"C:\\\\dir\",msg=\"a \\\"quoted\\\" word\"} 1\n");

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().labels())
                .containsEntry("path", "C:\\dir")
                .containsEntry("msg", "a \"quoted\" word");
    }

    @Test
    void shouldIgnoreCommentsBlanksAndExemplars() {
        var text = """
                # HELP something help text
                # TYPE something counter

                something_total{a="b"} 5 # {trace_id="abc"} 0.5
                """;

        var samples = parser.parse(text);

        assertThat(samples).hasSize(1);
        assertThat(samples.getFirst().value()).isEqualTo(5.0);
    }

    @Test
    void shouldSkipUnparseableLines_whenInputIsMalformed() {
        var samples = parser.parse("valid_metric 1.0\ngarbage line without value x\nbroken{unclosed 2\n");

        assertThat(samples).extracting(MetricSample::name).containsExactly("valid_metric");
    }

    @Test
    void shouldReturnEmpty_whenInputIsBlank() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("  \n ")).isEmpty();
    }

    private static MetricSample findOne(List<MetricSample> samples, String name) {
        return samples.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<MetricSample> findAll(List<MetricSample> samples, String name) {
        return samples.stream().filter(s -> s.name().equals(name)).toList();
    }

}
