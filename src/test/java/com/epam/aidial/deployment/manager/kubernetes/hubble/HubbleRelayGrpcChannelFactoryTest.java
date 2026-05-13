package com.epam.aidial.deployment.manager.kubernetes.hubble;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HubbleRelayGrpcChannelFactoryTest {

    @Test
    void parseLabelSelector_singleLabel() {
        assertThat(HubbleRelayGrpcChannelFactory.parseLabelSelector("k8s-app=hubble-relay"))
                .isEqualTo(Map.of("k8s-app", "hubble-relay"));
    }

    @Test
    void parseLabelSelector_multipleLabels() {
        assertThat(HubbleRelayGrpcChannelFactory.parseLabelSelector("k8s-app=hubble-relay,tier=control"))
                .isEqualTo(Map.of("k8s-app", "hubble-relay", "tier", "control"));
    }

    @Test
    void parseLabelSelector_stripsWhitespace() {
        assertThat(HubbleRelayGrpcChannelFactory.parseLabelSelector(" k8s-app = hubble-relay , tier = control "))
                .isEqualTo(Map.of("k8s-app", "hubble-relay", "tier", "control"));
    }

    @Test
    void parseLabelSelector_ignoresNonEqualityPairs() {
        // set-based operators are not supported — silently ignored
        assertThat(HubbleRelayGrpcChannelFactory.parseLabelSelector("app=relay,environment notin (dev,test)"))
                .isEqualTo(Map.of("app", "relay"));
    }

    @Test
    void parseLabelSelector_emptyString_returnsEmptyMap() {
        assertThat(HubbleRelayGrpcChannelFactory.parseLabelSelector("")).isEmpty();
    }
}
