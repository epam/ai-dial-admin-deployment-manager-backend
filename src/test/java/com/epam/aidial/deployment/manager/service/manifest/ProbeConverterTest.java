package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import io.fabric8.kubernetes.api.model.Probe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeConverterTest {

    private ProbeConverter probeConverter;

    @BeforeEach
    void setUp() {
        probeConverter = new ProbeConverter();
    }

    @Nested
    class ToProbe {

        @Test
        void nullProperties_returnsNull() {
            assertThat(probeConverter.toProbe(null)).isNull();
        }

        @Test
        void disabledProperties_returnsNull() {
            var properties = new ProbeProperties();
            properties.setEnabled(false);
            properties.setProbe(new HttpGetProbe("/health", 8080));

            assertThat(probeConverter.toProbe(properties)).isNull();
        }

        @Test
        void enabledWithNullProbe_returnsNull() {
            var properties = new ProbeProperties();
            properties.setEnabled(true);
            properties.setProbe(null);

            assertThat(probeConverter.toProbe(properties)).isNull();
        }

        @Test
        void httpGet_convertsToProbeWithHttpGetAndTiming() {
            var httpGet = new HttpGetProbe("/ready", 9090);
            var properties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

            Probe probe = probeConverter.toProbe(properties);

            assertThat(probe).isNotNull();
            assertThat(probe.getInitialDelaySeconds()).isEqualTo(5);
            assertThat(probe.getPeriodSeconds()).isEqualTo(10);
            assertThat(probe.getTimeoutSeconds()).isEqualTo(3);
            assertThat(probe.getFailureThreshold()).isEqualTo(2);
            assertThat(probe.getHttpGet()).isNotNull();
            assertThat(probe.getHttpGet().getPath()).isEqualTo("/ready");
            assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(9090);
        }

        @Test
        void httpGetWithPortNumber_usesIntOrStringPort() {
            var httpGet = new HttpGetProbe("/health", 8080);
            var properties = new ProbeProperties(true, null, null, null, null, httpGet);

            Probe probe = probeConverter.toProbe(properties);

            assertThat(probe).isNotNull();
            assertThat(probe.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        }
    }

    @Nested
    class ToKserveStartupProbe {

        @Test
        void nullProperties_returnsNull() {
            assertThat(probeConverter.toKserveStartupProbe(null)).isNull();
        }

        @Test
        void disabledProperties_returnsNull() {
            var properties = new ProbeProperties();
            properties.setEnabled(false);
            properties.setProbe(new HttpGetProbe("/health", 8080));

            assertThat(probeConverter.toKserveStartupProbe(properties)).isNull();
        }

        @Test
        void httpGet_returnsKserveStartupProbeWithHttpGet() {
            var httpGet = new HttpGetProbe("/health", 8080);
            var properties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

            var result = probeConverter.toKserveStartupProbe(properties);

            assertThat(result).isNotNull();
            assertThat(result.getHttpGet()).isNotNull();
            assertThat(result.getHttpGet().getPath()).isEqualTo("/health");
            assertThat(result.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
            assertThat(result.getInitialDelaySeconds()).isEqualTo(5);
            assertThat(result.getPeriodSeconds()).isEqualTo(10);
            assertThat(result.getTimeoutSeconds()).isEqualTo(3);
            assertThat(result.getFailureThreshold()).isEqualTo(2);
        }
    }

    @Nested
    class ToNimStartupProbe {

        @Test
        void nullProperties_returnsNull() {
            assertThat(probeConverter.toNimStartupProbe(null)).isNull();
        }

        @Test
        void disabledProperties_returnsNull() {
            var properties = new ProbeProperties();
            properties.setEnabled(false);
            properties.setProbe(new HttpGetProbe("/health", 8080));

            assertThat(probeConverter.toNimStartupProbe(properties)).isNull();
        }

        @Test
        void httpGet_returnsNimStartupProbeWithEnabledAndHttpGet() {
            var httpGet = new HttpGetProbe("/ready", 9090);
            var properties = new ProbeProperties(true, 1, 5, 1, 1, httpGet);

            var result = probeConverter.toNimStartupProbe(properties);

            assertThat(result).isNotNull();
            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getProbe()).isNotNull();
            assertThat(result.getProbe().getHttpGet()).isNotNull();
            assertThat(result.getProbe().getHttpGet().getPath()).isEqualTo("/ready");
            assertThat(result.getProbe().getHttpGet().getPort().getIntVal()).isEqualTo(9090);
            assertThat(result.getProbe().getInitialDelaySeconds()).isEqualTo(1);
            assertThat(result.getProbe().getPeriodSeconds()).isEqualTo(5);
        }
    }
}
