package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.model.probe.TcpSocketProbe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KserveProbeConverterTest {

    private final KserveProbeConverter kserveProbeConverter = new KserveProbeConverter(new ProbeConverter());

    @Test
    void nullProperties_returnsNull() {
        assertThat(kserveProbeConverter.toKserveStartupProbe((ProbeProperties) null)).isNull();
    }

    @Test
    void disabledProperties_returnsNull() {
        var properties = new ProbeProperties();
        properties.setEnabled(false);
        properties.setProbe(new HttpGetProbe("/health", 8080));

        assertThat(kserveProbeConverter.toKserveStartupProbe(properties)).isNull();
    }

    @Test
    void httpGet_returnsKserveStartupProbeWithHttpGet() {
        var httpGet = new HttpGetProbe("/health", 8080);
        var properties = new ProbeProperties(true, 5, 10, 3, 2, httpGet);

        var result = kserveProbeConverter.toKserveStartupProbe(properties);

        assertThat(result).isNotNull();
        assertThat(result.getHttpGet()).isNotNull();
        assertThat(result.getHttpGet().getPath()).isEqualTo("/health");
        assertThat(result.getHttpGet().getPort().getIntVal()).isEqualTo(8080);
        assertThat(result.getInitialDelaySeconds()).isEqualTo(5);
        assertThat(result.getPeriodSeconds()).isEqualTo(10);
        assertThat(result.getTimeoutSeconds()).isEqualTo(3);
        assertThat(result.getFailureThreshold()).isEqualTo(2);
    }

    @Test
    void tcpSocket_returnsKserveStartupProbeWithTcpSocket() {
        var tcpSocket = new TcpSocketProbe(6379);
        var properties = new ProbeProperties(true, 5, 10, 3, 2, tcpSocket);

        var result = kserveProbeConverter.toKserveStartupProbe(properties);

        assertThat(result).isNotNull();
        assertThat(result.getTcpSocket()).isNotNull();
        assertThat(result.getTcpSocket().getPort().getIntVal()).isEqualTo(6379);
        assertThat(result.getInitialDelaySeconds()).isEqualTo(5);
        assertThat(result.getPeriodSeconds()).isEqualTo(10);
    }
}
