package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Affinity;
import io.kserve.serving.v1beta1.inferenceservicespec.predictor.Tolerations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoolPrimitivesConverterTest {

    private PoolPrimitivesConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PoolPrimitivesConverter(JsonMapperConfiguration.createJsonMapper());
    }

    @Test
    void shouldReturnNull_whenSourceAffinityIsNull() {
        var result = converter.convertAffinity(null, Affinity.class);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnEmptyList_whenSourceTolerationsIsNull() {
        var result = converter.convertTolerations(null, Tolerations.class);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenSourceTolerationsIsEmpty() {
        var result = converter.convertTolerations(List.of(), Tolerations.class);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldRoundTripAffinityToKserveSpecType() {
        var fabric8Affinity = new AffinityBuilder()
                .withNewNodeAffinity()
                .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                .addNewNodeSelectorTerm()
                .addNewMatchExpression()
                .withKey("zone").withOperator("In").addToValues("us-east-1a")
                .endMatchExpression()
                .endNodeSelectorTerm()
                .endRequiredDuringSchedulingIgnoredDuringExecution()
                .endNodeAffinity()
                .build();

        var converted = converter.convertAffinity(
                fabric8Affinity, Affinity.class);

        assertThat(converted).isNotNull();
        assertThat(converted.getNodeAffinity()).isNotNull();
        var required = converted.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution();
        assertThat(required).isNotNull();
        assertThat(required.getNodeSelectorTerms().get(0).getMatchExpressions().get(0).getKey()).isEqualTo("zone");
    }
}
