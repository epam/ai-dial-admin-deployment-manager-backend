package com.epam.aidial.deployment.manager.web.controller.none;

import com.epam.aidial.deployment.manager.configuration.JsonMapperConfiguration;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
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
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NodePoolController.class)
@Import({JsonMapperConfiguration.class, NodePoolDtoMapperImpl.class})
class NodePoolControllerTest extends AbstractControllerNoneSecureTest {

    @MockitoBean
    private NodePoolService nodePoolService;

    @Test
    void testGetNodePoolsWithPrimitives() throws Exception {
        var gpuPool = new PoolConfig();
        gpuPool.setId("gpu-pool");
        gpuPool.setName("GPU pool");
        gpuPool.setDescription("GPU pool");
        gpuPool.setNodeSelector(Map.of("accelerator-type", "nvidia-a100"));

        var cpuPool = new PoolConfig();
        cpuPool.setId("cpu-pool");
        cpuPool.setName("CPU pool");
        cpuPool.setDescription("CPU pool");

        var properties = new NodePoolProperties();
        properties.setPools(List.of(gpuPool, cpuPool));
        properties.setDefaultPoolId("cpu-pool");
        properties.setDefaultModelPoolId("gpu-pool");

        doReturn(properties).when(nodePoolService).getProperties();

        var expectedJson = ResourceUtils.readResource("/nodepool/node_pools_response.json");
        mockMvc.perform(get("/api/v1/node-pools"))
                .andExpect(status().isOk())
                .andExpect(content().json(expectedJson, JsonCompareMode.STRICT));
    }

    @Test
    void testGetNodePoolsEmpty() throws Exception {
        var properties = new NodePoolProperties();
        properties.setPools(List.of());

        doReturn(properties).when(nodePoolService).getProperties();

        mockMvc.perform(get("/api/v1/node-pools"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"pools\":[]}", JsonCompareMode.STRICT));
    }
}
