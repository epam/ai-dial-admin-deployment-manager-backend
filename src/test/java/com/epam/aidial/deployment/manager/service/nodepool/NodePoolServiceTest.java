package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.CpuSpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.GpuSpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.MemorySpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.NodePoolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
        doReturn(null).when(nodePoolProperties).getNodePools();

        var result = nodePoolService.getNodePools();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenPoolsListIsEmpty() {
        doReturn(List.of()).when(nodePoolProperties).getNodePools();

        var result = nodePoolService.getNodePools();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnConfiguredPools() {
        var gpu = new GpuSpec();
        gpu.setName("NVIDIA A100");
        gpu.setVramBytes(85899345920L);
        gpu.setCount(4);

        var cpu = new CpuSpec();
        cpu.setName("AMD EPYC Milan");
        cpu.setMilliCpus(48000);

        var memory = new MemorySpec();
        memory.setBytes(730144440320L);

        var config = new NodePoolConfig();
        config.setName("gpu-a100");
        config.setDescription("GPU pool");
        config.setInstance("a2-ultragpu-4g");
        config.setMinNodes(2);
        config.setMaxNodes(10);
        config.setGpu(gpu);
        config.setCpu(cpu);
        config.setMemory(memory);

        doReturn(List.of(config)).when(nodePoolProperties).getNodePools();

        var result = nodePoolService.getNodePools();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("gpu-a100");
        assertThat(result.get(0).getDescription()).isEqualTo("GPU pool");
        assertThat(result.get(0).getInstance()).isEqualTo("a2-ultragpu-4g");
        assertThat(result.get(0).getMinNodes()).isEqualTo(2);
        assertThat(result.get(0).getMaxNodes()).isEqualTo(10);
        assertThat(result.get(0).getGpu()).isNotNull();
        assertThat(result.get(0).getGpu().getName()).isEqualTo("NVIDIA A100");
        assertThat(result.get(0).getGpu().getVramBytes()).isEqualTo(85899345920L);
        assertThat(result.get(0).getGpu().getCount()).isEqualTo(4);
        assertThat(result.get(0).getCpu().getName()).isEqualTo("AMD EPYC Milan");
        assertThat(result.get(0).getCpu().getMilliCpus()).isEqualTo(48000);
        assertThat(result.get(0).getMemory().getBytes()).isEqualTo(730144440320L);
    }

    @Test
    void shouldReturnCpuOnlyPool_withNullGpu() {
        var cpu = new CpuSpec();
        cpu.setMilliCpus(64000);

        var memory = new MemorySpec();
        memory.setBytes(549755813888L);

        var config = new NodePoolConfig();
        config.setName("cpu-pool");
        config.setMaxNodes(5);
        config.setCpu(cpu);
        config.setMemory(memory);

        doReturn(List.of(config)).when(nodePoolProperties).getNodePools();

        var result = nodePoolService.getNodePools();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("cpu-pool");
        assertThat(result.get(0).getDescription()).isNull();
        assertThat(result.get(0).getInstance()).isNull();
        assertThat(result.get(0).getMinNodes()).isZero();
        assertThat(result.get(0).getGpu()).isNull();
        assertThat(result.get(0).getCpu().getName()).isNull();
        assertThat(result.get(0).getCpu().getMilliCpus()).isEqualTo(64000);
        assertThat(result.get(0).getMemory().getBytes()).isEqualTo(549755813888L);
    }
}
