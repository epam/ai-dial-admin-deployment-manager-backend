package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.metrics.AvailabilityStatus;
import com.epam.aidial.deployment.manager.model.metrics.DistributionSummary;
import com.epam.aidial.deployment.manager.model.metrics.OperationalMetrics;
import com.epam.aidial.deployment.manager.model.metrics.PodResourceUsage;
import com.epam.aidial.deployment.manager.model.metrics.ResourceMetrics;
import com.epam.aidial.deployment.manager.model.metrics.ServingMetrics;
import com.epam.aidial.deployment.manager.model.metrics.UnifiedDeploymentMetrics;
import com.epam.aidial.deployment.manager.web.dto.metrics.DeploymentMetricsDto;
import com.epam.aidial.deployment.manager.web.dto.metrics.DistributionSummaryDto;
import com.epam.aidial.deployment.manager.web.dto.metrics.MetricsAvailabilityDto;
import com.epam.aidial.deployment.manager.web.dto.metrics.OperationalMetricsDto;
import com.epam.aidial.deployment.manager.web.dto.metrics.ResourceMetricsDto;
import com.epam.aidial.deployment.manager.web.dto.metrics.ServingMetricsDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeploymentMetricsDtoMapper {

    DeploymentMetricsDto toDto(UnifiedDeploymentMetrics metrics);

    DistributionSummaryDto toDto(DistributionSummary summary);

    MetricsAvailabilityDto toDto(AvailabilityStatus availability);

    OperationalMetricsDto toDto(OperationalMetrics operational);

    @Mapping(target = "tokensPerSecond.prompt", source = "promptTokensPerSecond")
    @Mapping(target = "tokensPerSecond.generation", source = "generationTokensPerSecond")
    ServingMetricsDto toDto(ServingMetrics serving);

    @Mapping(target = "replicas.total", source = "replicasTotal")
    @Mapping(target = "replicas.ready", source = "replicasReady")
    ResourceMetricsDto toDto(ResourceMetrics resources);

    ResourceMetricsDto.PodResourceUsageDto toDto(PodResourceUsage usage);

}
