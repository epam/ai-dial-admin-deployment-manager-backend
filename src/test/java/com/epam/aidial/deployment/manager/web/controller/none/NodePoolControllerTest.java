package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.service.nodepool.NodePoolService;
import com.epam.aidial.deployment.manager.utils.ResourceUtils;
import com.epam.aidial.deployment.manager.web.controller.NodePoolController;
import com.epam.aidial.deployment.manager.web.dto.nodepool.CpuSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.GpuSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.MemorySpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
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
@Import({JsonMapperConfiguration.class})
class NodePoolControllerTest extends AbstractControllerNoneSecureTest {

    @MockitoBean
    private NodePoolService nodePoolService;

    @Test
    void testGetNodePools() throws Exception {
        // Given
        var gpuPool = new NodePoolDto(
                "gpu-a100-prod",
                "LLM inference & fine-tuning",
                "a2-ultragpu-4g",
                2,
                10,
                new GpuSpecDto("NVIDIA A100", 85899345920L, 4),
                new CpuSpecDto("AMD EPYC Milan", 48000),
                new MemorySpecDto(730144440320L)
        );
        var cpuPool = new NodePoolDto(
                "cpu-highmem",
                "Data preprocessing",
                null,
                0,
                5,
                null,
                new CpuSpecDto(null, 64000),
                new MemorySpecDto(549755813888L)
        );
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
