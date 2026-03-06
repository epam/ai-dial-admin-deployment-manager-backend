package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.model.probe.HttpGetProbe;
import com.epam.aidial.deployment.manager.model.probe.ProbeProperties;
import com.epam.aidial.deployment.manager.model.probe.TcpSocketProbe;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NimProbeConverterTest {

    private final NimProbeConverter nimProbeConverter = new NimProbeConverter(new ProbeConverter());

    @Test
    void nullProperties_returnsNull() {
        assertThat(nimProbeConverter.toNimStartupProbe((ProbeProperties) null)).isNull();
    }

    @Test
    void disabledProperties_returnsNull() {
        var properties = new ProbeProperties();
        properties.setEnabled(false);
        properties.setProbe(new HttpGetProbe("/health", 8080));

        assertThat(nimProbeConverter.toNimStartupProbe(properties)).isNull();
    }

    @Test
    void httpGet_returnsNimStartupProbeWithEnabledAndHttpGet() {
        var httpGet = new HttpGetProbe("/ready", 9090);
        var properties = new ProbeProperties(true, 1, 5, 1, 1, httpGet);

        var result = nimProbeConverter.toNimStartupProbe(properties);

        assertThat(result).isNotNull();
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getProbe()).isNotNull();
        assertThat(result.getProbe().getHttpGet()).isNotNull();
        assertThat(result.getProbe().getHttpGet().getPath()).isEqualTo("/ready");
        assertThat(result.getProbe().getHttpGet().getPort().getIntVal()).isEqualTo(9090);
        assertThat(result.getProbe().getInitialDelaySeconds()).isEqualTo(1);
        assertThat(result.getProbe().getPeriodSeconds()).isEqualTo(5);
    }

    @Test
    void tcpSocket_returnsNimStartupProbeWithEnabledAndTcpSocket() {
        var tcpSocket = new TcpSocketProbe(6379);
        var properties = new ProbeProperties(true, 1, 5, 1, 1, tcpSocket);

        var result = nimProbeConverter.toNimStartupProbe(properties);

        assertThat(result).isNotNull();
        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getProbe()).isNotNull();
        assertThat(result.getProbe().getTcpSocket()).isNotNull();
        assertThat(result.getProbe().getTcpSocket().getPort().getIntVal()).isEqualTo(6379);
        assertThat(result.getProbe().getInitialDelaySeconds()).isEqualTo(1);
        assertThat(result.getProbe().getPeriodSeconds()).isEqualTo(5);
    }
}
