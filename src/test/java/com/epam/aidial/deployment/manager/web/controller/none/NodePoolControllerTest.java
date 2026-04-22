package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.CpuSpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.GpuSpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.MemorySpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.NodePoolConfig;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.NodePoolController;
import com.epam.aidial.deployment.manager.web.mapper.NodePoolDtoMapperImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NodePoolController.class)
@Import({JsonMapperConfiguration.class, NodePoolDtoMapperImpl.class})
class NodePoolControllerTest extends AbstractControllerNoneSecureTest {

    @MockitoBean
    private NodePoolService nodePoolService;

    @Test
    void testGetNodePools() throws Exception {
        // Given
        var gpu = new GpuSpec();
        gpu.setName("NVIDIA A100");
        gpu.setVramBytes(85899345920L);
        gpu.setCount(4);

        var gpuCpu = new CpuSpec();
        gpuCpu.setName("AMD EPYC Milan");
        gpuCpu.setMilliCpus(48000);

        var gpuMemory = new MemorySpec();
        gpuMemory.setBytes(730144440320L);

        var gpuPool = new NodePoolConfig();
        gpuPool.setName("gpu-a100-prod");
        gpuPool.setDescription("LLM inference & fine-tuning");
        gpuPool.setInstance("a2-ultragpu-4g");
        gpuPool.setMinNodes(2);
        gpuPool.setMaxNodes(10);
        gpuPool.setGpu(gpu);
        gpuPool.setCpu(gpuCpu);
        gpuPool.setMemory(gpuMemory);

        var cpuCpu = new CpuSpec();
        cpuCpu.setMilliCpus(64000);

        var cpuMemory = new MemorySpec();
        cpuMemory.setBytes(549755813888L);

        var cpuPool = new NodePoolConfig();
        cpuPool.setName("cpu-highmem");
        cpuPool.setDescription("Data preprocessing");
        cpuPool.setMaxNodes(5);
        cpuPool.setCpu(cpuCpu);
        cpuPool.setMemory(cpuMemory);

        doReturn(List.of(gpuPool, cpuPool)).when(nodePoolService).getNodePools();

        // When & Then
        var expectedJson = ResourceUtils.readResource("/nodepool/node_pools_response.json");
        mockMvc.perform(get("/api/v1/node-pools"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson, JsonCompareMode.STRICT));

        verify(nodePoolService).getNodePools();
    }

    @Test
    void testGetNodePoolsEmpty() throws Exception {
        // Given
        doReturn(List.of()).when(nodePoolService).getNodePools();

        // When & Then
        mockMvc.perform(get("/api/v1/node-pools"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", JsonCompareMode.STRICT));

        verify(nodePoolService).getNodePools();
    }
}
