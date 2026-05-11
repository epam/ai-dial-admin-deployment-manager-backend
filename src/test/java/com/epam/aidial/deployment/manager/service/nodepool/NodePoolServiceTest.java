package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.epam.aidial.deployment.manager.model.deployment.CreateInferenceDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateMcpDeployment;
import com.epam.aidial.deployment.manager.model.deployment.CreateNimDeployment;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.Toleration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class NodePoolServiceTest {

    @Mock
    private NodePoolProperties nodePoolProperties;

    @InjectMocks
    private NodePoolService nodePoolService;

    @Test
    void shouldReturnEmptyList_whenNoPoolsConfigured() {
        doReturn(null).when(nodePoolProperties).getPools();

        assertThat(nodePoolService.getNodePools()).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenPoolsListIsEmpty() {
        doReturn(List.of()).when(nodePoolProperties).getPools();

        assertThat(nodePoolService.getNodePools()).isEmpty();
    }

    @Test
    void shouldReturnConfiguredPoolsWithPrimitives() {
        var config = poolWithPrimitives("gpu_pool", Map.of("accelerator", "a100"), new Affinity(), List.of(new Toleration()));

        doReturn(List.of(config)).when(nodePoolProperties).getPools();

        var result = nodePoolService.getNodePools();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("gpu_pool");
        assertThat(result.get(0).getNodeSelector()).containsEntry("accelerator", "a100");
        assertThat(result.get(0).getAffinity()).isNotNull();
        assertThat(result.get(0).getTolerations()).hasSize(1);
    }

    @Test
    void shouldResolveModelOverrideForModelWorkload() {
        doReturn("gpu_pool").when(nodePoolProperties).getDefaultModelPool();

        var result = nodePoolService.resolveForCreate(new CreateNimDeployment());

        assertThat(result).isEqualTo("gpu_pool");
    }

    @Test
    void shouldResolveModelOverrideForKserveInferenceWorkload() {
        doReturn("gpu_pool").when(nodePoolProperties).getDefaultModelPool();

        var result = nodePoolService.resolveForCreate(new CreateInferenceDeployment());

        assertThat(result).isEqualTo("gpu_pool");
    }

    @Test
    void shouldFallThroughToCatchAllForNonModelWorkload() {
        doReturn("cpu_pool").when(nodePoolProperties).getDefaultPool();

        var result = nodePoolService.resolveForCreate(new CreateMcpDeployment());

        assertThat(result).isEqualTo("cpu_pool");
    }

    @Test
    void shouldFallThroughToCatchAllForModelWorkload_whenModelOverrideUnset() {
        doReturn(null).when(nodePoolProperties).getDefaultModelPool();
        doReturn("cpu_pool").when(nodePoolProperties).getDefaultPool();

        var result = nodePoolService.resolveForCreate(new CreateNimDeployment());

        assertThat(result).isEqualTo("cpu_pool");
    }

    @Test
    void shouldResolveToNull_whenNoDefaultsConfigured() {
        doReturn(null).when(nodePoolProperties).getDefaultPool();

        var result = nodePoolService.resolveForCreate(new CreateMcpDeployment());

        assertThat(result).isNull();
    }

    @Test
    void shouldResolveToNullForModelWorkload_whenNoDefaultsConfigured() {
        doReturn(null).when(nodePoolProperties).getDefaultModelPool();
        doReturn(null).when(nodePoolProperties).getDefaultPool();

        var result = nodePoolService.resolveForCreate(new CreateNimDeployment());

        assertThat(result).isNull();
    }

    private static PoolConfig poolWithPrimitives(String name,
                                                 Map<String, String> nodeSelector,
                                                 Affinity affinity,
                                                 List<Toleration> tolerations) {
        var pool = new PoolConfig();
        pool.setName(name);
        pool.setNodeSelector(nodeSelector);
        pool.setAffinity(affinity);
        pool.setTolerations(tolerations);
        return pool;
    }
}
