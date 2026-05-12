package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolListResponseDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolListResponseDto.DefaultsDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface NodePoolDtoMapper {

    NodePoolDto toNodePoolDto(PoolConfig config);

    List<NodePoolDto> toNodePoolDtoList(List<PoolConfig> configs);

    default NodePoolListResponseDto toListResponse(NodePoolProperties properties) {
        var pools = toNodePoolDtoList(properties.getPools());
        var defaults = new DefaultsDto(properties.getDefaultPoolId(), properties.getDefaultModelPoolId());
        return new NodePoolListResponseDto(pools, defaults.isEmpty() ? null : defaults);
    }
}
