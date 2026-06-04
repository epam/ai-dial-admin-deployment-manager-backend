package com.epam.aidial.deployment.manager.configuration;

import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.logging.otlp.OtlpLoggingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the OpenTelemetry wiring in {@code application.yml}: export must stay off by default (Spring Boot defaults
 * all OTLP export flags to {@code true}), the {@code OTEL_*} environment variables must reach the right
 * {@code management.*} properties, and the context must keep starting when no OTEL variables are set at all — the
 * endpoint properties are evaluated by auto-configuration conditions even with export disabled, so they must always
 * resolve (see the base endpoint default in {@code application.yml}).
 */
class OpenTelemetryExportConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    OpenTelemetrySdkAutoConfiguration.class,
                    OpenTelemetryLoggingAutoConfiguration.class,
                    OtlpLoggingAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    OtlpTracingAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    OtlpMetricsExportAutoConfiguration.class));

    @Test
    void exportIsDisabledByDefaultAndUnsetEndpointIsHarmless() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
            assertThat(context).doesNotHaveBean(OtlpGrpcSpanExporter.class);
            assertThat(context).doesNotHaveBean(OtlpHttpLogRecordExporter.class);
            assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
        });
    }

    @Test
    void masterSwitchEnablesAllSignals() {
        contextRunner
                .withPropertyValues("OTEL_EXPORT_ENABLED=true", "OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4318")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                    assertThat(context).hasSingleBean(OtlpHttpLogRecordExporter.class);
                    assertThat(context).hasSingleBean(OtlpMeterRegistry.class);
                });
    }

    @Test
    void enablingExportWithoutEndpointFallsBackToOtelSpecDefault() {
        contextRunner
                .withPropertyValues("OTEL_EXPORT_ENABLED=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OtlpHttpSpanExporter.class).toString()).contains("http://localhost:4318/v1/traces");
                });
    }

    @Test
    void perSignalEndpointOverrideWorksWithoutBaseEndpoint() {
        contextRunner
                .withPropertyValues("OTEL_TRACES_EXPORT_ENABLED=true", "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://collector:4318/v1/traces")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpLogRecordExporter.class);
                    assertThat(context).doesNotHaveBean(OtlpMeterRegistry.class);
                });
    }

    @Test
    void grpcTransportCreatesGrpcExporter() {
        contextRunner
                .withPropertyValues(
                        "OTEL_TRACES_EXPORT_ENABLED=true",
                        "OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4317",
                        "OTEL_EXPORTER_OTLP_TRANSPORT=grpc")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OtlpGrpcSpanExporter.class);
                    assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
                });
    }
}
