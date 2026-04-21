package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.NodePoolConfig;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.kubernetes.K8sClient;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodePoolDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodeSpecDto;
import com.epam.aidial.deployment.manager.web.dto.nodepool.NodeUtilizationDto;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class NodePoolService {

    private static final String GPU_RESOURCE_KEY = "nvidia.com/gpu";
    private static final String CPU_RESOURCE_KEY = "cpu";
    private static final String MEMORY_RESOURCE_KEY = "memory";

    private final NodePoolProperties nodePoolProperties;
    private final K8sClient k8sClient;

    public List<NodePoolDto> getNodePools() {
        var pools = nodePoolProperties.getNodePools();
        if (CollectionUtils.isEmpty(pools)) {
            return List.of();
        }

        var result = new ArrayList<NodePoolDto>();
        for (var poolConfig : pools) {
            var nodePoolDto = buildNodePoolDto(poolConfig);
            result.add(nodePoolDto);
        }
        return result;
    }

    private NodePoolDto buildNodePoolDto(NodePoolConfig poolConfig) {
        var nodes = k8sClient.listNodes(poolConfig.getLabelSelector());
        var nodeUtilizations = new ArrayList<NodeUtilizationDto>();

        for (var node : nodes) {
            var utilization = buildNodeUtilization(node);
            nodeUtilizations.add(utilization);
        }

        var nodeSpec = new NodeSpecDto(
                poolConfig.getNodeSpec().getCpuMillis(),
                poolConfig.getNodeSpec().getMemoryBytes(),
                poolConfig.getNodeSpec().getGpu()
        );

        return new NodePoolDto(
                poolConfig.getName(),
                poolConfig.getDescription(),
                poolConfig.getMaxNodes(),
                nodes.size(),
                nodeSpec,
                nodeUtilizations
        );
    }

    private NodeUtilizationDto buildNodeUtilization(Node node) {
        var nodeName = node.getMetadata().getName();
        var allocatable = node.getStatus().getAllocatable();

        long allocatableCpuMillis = quantityToMilliCpu(allocatable.get(CPU_RESOURCE_KEY));
        long allocatableMemoryBytes = quantityToBytes(allocatable.get(MEMORY_RESOURCE_KEY));
        int allocatableGpu = quantityToInt(allocatable.get(GPU_RESOURCE_KEY));

        var podList = k8sClient.listAllPodsOnNode(nodeName);
        long requestedCpuMillis = 0;
        long requestedMemoryBytes = 0;
        int requestedGpu = 0;

        for (var pod : podList.getItems()) {
            if (isPodScheduled(pod)) {
                var podResources = sumPodRequests(pod);
                requestedCpuMillis += podResources[0];
                requestedMemoryBytes += podResources[1];
                requestedGpu += (int) podResources[2];
            }
        }

        return new NodeUtilizationDto(
                nodeName,
                allocatableCpuMillis,
                allocatableMemoryBytes,
                allocatableGpu,
                requestedCpuMillis,
                requestedMemoryBytes,
                requestedGpu
        );
    }

    private boolean isPodScheduled(Pod pod) {
        var phase = pod.getStatus().getPhase();
        return "Running".equals(phase) || "Pending".equals(phase);
    }

    private long[] sumPodRequests(Pod pod) {
        long cpuMillis = 0;
        long memoryBytes = 0;
        long gpu = 0;

        var containers = pod.getSpec().getContainers();
        if (CollectionUtils.isNotEmpty(containers)) {
            for (var container : containers) {
                var requests = getContainerRequests(container);
                cpuMillis += quantityToMilliCpu(requests.get(CPU_RESOURCE_KEY));
                memoryBytes += quantityToBytes(requests.get(MEMORY_RESOURCE_KEY));
                gpu += quantityToInt(requests.get(GPU_RESOURCE_KEY));
            }
        }

        var initContainers = pod.getSpec().getInitContainers();
        if (CollectionUtils.isNotEmpty(initContainers)) {
            for (var container : initContainers) {
                var requests = getContainerRequests(container);
                cpuMillis = Math.max(cpuMillis, quantityToMilliCpu(requests.get(CPU_RESOURCE_KEY)));
                memoryBytes = Math.max(memoryBytes, quantityToBytes(requests.get(MEMORY_RESOURCE_KEY)));
                gpu = Math.max(gpu, quantityToInt(requests.get(GPU_RESOURCE_KEY)));
            }
        }

        return new long[]{cpuMillis, memoryBytes, gpu};
    }

    private Map<String, Quantity> getContainerRequests(Container container) {
        if (container.getResources() == null || container.getResources().getRequests() == null) {
            return Map.of();
        }
        return container.getResources().getRequests();
    }

    private long quantityToMilliCpu(Quantity quantity) {
        if (quantity == null) {
            return 0;
        }
        return Quantity.getAmountInBytes(quantity)
                .movePointRight(3)
                .longValue();
    }

    private long quantityToBytes(Quantity quantity) {
        if (quantity == null) {
            return 0;
        }
        return Quantity.getAmountInBytes(quantity).longValue();
    }

    private int quantityToInt(Quantity quantity) {
        if (quantity == null) {
            return 0;
        }
        return Quantity.getAmountInBytes(quantity).intValue();
    }
}
