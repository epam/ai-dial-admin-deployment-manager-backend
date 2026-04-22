package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.NodePoolConfig;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.web.dto.nodepool.CpuSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.GpuSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.MemorySpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class NodePoolService {

    private final NodePoolProperties nodePoolProperties;

    public List<NodePoolDto> getNodePools() {
        var pools = nodePoolProperties.getNodePools();
        if (CollectionUtils.isEmpty(pools)) {
            return List.of();
        }

        return pools.stream()
                .map(this::toDto)
                .toList();
    }

    private NodePoolDto toDto(NodePoolConfig config) {
        GpuSpecDto gpuDto = null;
        if (config.getGpu() != null) {
            gpuDto = new GpuSpecDto(
                    config.getGpu().getName(),
                    config.getGpu().getVramBytes(),
                    config.getGpu().getCount()
            );
        }

        var cpuDto = new CpuSpecDto(
                config.getCpu().getName(),
                config.getCpu().getMilliCpus()
        );

        var memoryDto = new MemorySpecDto(config.getMemory().getBytes());

        return new NodePoolDto(
                config.getName(),
                config.getDescription(),
                config.getInstance(),
                config.getMaxNodes(),
                gpuDto,
                cpuDto,
                memoryDto
        );
    }
}
