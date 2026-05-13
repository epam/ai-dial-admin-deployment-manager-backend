package com.epam.aidial.deployment.manager.service.manifest;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.nvidia.apps.v1alpha1.nimservicespec.Affinity;
import com.nvidia.apps.v1alpha1.nimservicespec.Tolerations;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
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
    void shouldRoundTripAffinityToNimSpecTypePreservingMatchExpressions() {
        var fabric8Affinity = new AffinityBuilder()
                .withNewNodeAffinity()
                .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                .addNewNodeSelectorTerm()
                .addNewMatchExpression()
                .withKey("accelerator-type").withOperator("In").addToValues("nvidia-a100", "nvidia-h100")
                .endMatchExpression()
                .endNodeSelectorTerm()
                .endRequiredDuringSchedulingIgnoredDuringExecution()
                .endNodeAffinity()
                .build();

        var converted = converter.convertAffinity(fabric8Affinity, Affinity.class);

        assertThat(converted).isNotNull();
        assertThat(converted.getNodeAffinity()).isNotNull();
        var required = converted.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution();
        assertThat(required).isNotNull();
        assertThat(required.getNodeSelectorTerms()).hasSize(1);
        var match = required.getNodeSelectorTerms().get(0).getMatchExpressions().get(0);
        assertThat(match.getKey()).isEqualTo("accelerator-type");
        assertThat(match.getOperator()).isEqualTo("In");
        assertThat(match.getValues()).containsExactly("nvidia-a100", "nvidia-h100");
    }

    @Test
    void shouldRoundTripTolerationsToNimSpecTypePreservingFields() {
        var fabric8Tolerations = List.of(
                new TolerationBuilder().withKey("dedicated").withOperator("Equal").withValue("gpu").withEffect("NoSchedule").build(),
                new TolerationBuilder().withKey("workload").withOperator("Exists").withEffect("PreferNoSchedule").build()
        );

        var converted = converter.convertTolerations(fabric8Tolerations, Tolerations.class);

        assertThat(converted).isNotNull().hasSize(2);
        assertThat(converted.get(0).getKey()).isEqualTo("dedicated");
        assertThat(converted.get(0).getValue()).isEqualTo("gpu");
        assertThat(converted.get(0).getEffect()).isEqualTo("NoSchedule");
        assertThat(converted.get(1).getKey()).isEqualTo("workload");
        assertThat(converted.get(1).getEffect()).isEqualTo("PreferNoSchedule");
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
                fabric8Affinity, io.kserve.serving.v1beta1.inferenceservicespec.predictor.Affinity.class);

        assertThat(converted).isNotNull();
        assertThat(converted.getNodeAffinity()).isNotNull();
        var required = converted.getNodeAffinity().getRequiredDuringSchedulingIgnoredDuringExecution();
        assertThat(required).isNotNull();
        assertThat(required.getNodeSelectorTerms().get(0).getMatchExpressions().get(0).getKey()).isEqualTo("zone");
    }
}
