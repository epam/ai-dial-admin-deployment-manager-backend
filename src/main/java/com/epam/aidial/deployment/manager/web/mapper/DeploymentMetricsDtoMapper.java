package com.epam.aidial.deployment.manager.web.mapper;

import com.epam.aidial.deployment.manager.model.metrics.BlockAvailability;
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

import java.util.List;

@Mapper(componentModel = "spring")
public interface DeploymentMetricsDtoMapper {

    DeploymentMetricsDto toDto(UnifiedDeploymentMetrics metrics);

    DistributionSummaryDto toDto(DistributionSummary summary);

    MetricsAvailabilityDto toDto(BlockAvailability availability);

    OperationalMetricsDto toDto(OperationalMetrics operational);

    default ServingMetricsDto toDto(ServingMetrics serving) {
        if (serving == null) {
            return null;
        }
        return new ServingMetricsDto(
                toDto(serving.ttft()),
                toDto(serving.interTokenLatency()),
                new ServingMetricsDto.TokensPerSecondDto(serving.promptTokensPerSecond(), serving.generationTokensPerSecond()),
                serving.queueDepth(),
                serving.runningRequests(),
                serving.kvCacheUsage());
    }

    default ResourceMetricsDto toDto(ResourceMetrics resources) {
        if (resources == null) {
            return null;
        }
        List<ResourceMetricsDto.PodResourceUsageDto> pods = resources.pods().stream()
                .map(this::toDto)
                .toList();
        return new ResourceMetricsDto(
                new ResourceMetricsDto.ReplicasDto(resources.replicasTotal(), resources.replicasReady()),
                pods);
    }

    ResourceMetricsDto.PodResourceUsageDto toDto(PodResourceUsage usage);

}
