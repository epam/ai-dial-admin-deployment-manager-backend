package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolListResponseDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NodePoolDtoMapper {

    NodePoolDto toNodePoolDto(PoolConfig config);

    List<NodePoolDto> toNodePoolDtoList(List<PoolConfig> configs);

    default NodePoolListResponseDto toListResponse(List<PoolConfig> pools) {
        return new NodePoolListResponseDto(toNodePoolDtoList(pools == null ? List.of() : pools));
    }
}
