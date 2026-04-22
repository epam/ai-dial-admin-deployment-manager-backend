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
    void shouldMapGpuPool() {
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
        var dto = result.get(0);
        assertThat(dto.name()).isEqualTo("gpu-a100");
        assertThat(dto.description()).isEqualTo("GPU pool");
        assertThat(dto.instance()).isEqualTo("a2-ultragpu-4g");
        assertThat(dto.minNodes()).isEqualTo(2);
        assertThat(dto.maxNodes()).isEqualTo(10);
        assertThat(dto.gpu()).isNotNull();
        assertThat(dto.gpu().name()).isEqualTo("NVIDIA A100");
        assertThat(dto.gpu().vramBytes()).isEqualTo(85899345920L);
        assertThat(dto.gpu().count()).isEqualTo(4);
        assertThat(dto.cpu().name()).isEqualTo("AMD EPYC Milan");
        assertThat(dto.cpu().milliCpus()).isEqualTo(48000);
        assertThat(dto.memory().bytes()).isEqualTo(730144440320L);
    }

    @Test
    void shouldMapCpuOnlyPool_withNullGpu() {
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
        var dto = result.get(0);
        assertThat(dto.name()).isEqualTo("cpu-pool");
        assertThat(dto.description()).isNull();
        assertThat(dto.instance()).isNull();
        assertThat(dto.minNodes()).isZero();
        assertThat(dto.gpu()).isNull();
        assertThat(dto.cpu().name()).isNull();
        assertThat(dto.cpu().milliCpus()).isEqualTo(64000);
        assertThat(dto.memory().bytes()).isEqualTo(549755813888L);
    }
}
