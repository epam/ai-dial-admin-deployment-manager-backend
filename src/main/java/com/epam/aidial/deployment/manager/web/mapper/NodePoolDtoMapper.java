package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.CpuSpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.GpuSpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.MemorySpec;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.NodePoolConfig;
import com.epam.aidial.deployment.manager.web.dto.nodepool.CpuSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.GpuSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.MemorySpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NodePoolDtoMapper {

    NodePoolDto toNodePoolDto(NodePoolConfig config);

    List<NodePoolDto> toNodePoolDtoList(List<NodePoolConfig> configs);

    GpuSpecDto toGpuSpecDto(GpuSpec gpu);

    CpuSpecDto toCpuSpecDto(CpuSpec cpu);

    MemorySpecDto toMemorySpecDto(MemorySpec memory);
}
