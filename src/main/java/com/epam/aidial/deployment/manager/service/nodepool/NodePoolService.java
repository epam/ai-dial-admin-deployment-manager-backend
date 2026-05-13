package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.model.deployment.CreateDeployment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@LogExecution
@RequiredArgsConstructor
public class NodePoolService {

    private final NodePoolProperties nodePoolProperties;

    public List<PoolConfig> getNodePools() {
        var pools = nodePoolProperties.getPools();
        if (CollectionUtils.isEmpty(pools)) {
            return List.of();
        }
        return List.copyOf(pools);
    }

    public Optional<PoolConfig> findById(String id) {
        return nodePoolProperties.findById(id);
    }

    /**
     * Create-time cascade resolution (FR-018). Called when the create payload omits the {@code nodePool}
     * field. Returns the value to stamp onto the deployment record.
     *
     * <ol>
     *   <li>{@code NODE_POOL_DEFAULT_MODEL} if set and the deployment is a model workload</li>
     *   <li>{@code NODE_POOL_DEFAULT} if set</li>
     *   <li>{@code null} ("Any")</li>
     * </ol>
     */
    @Nullable
    public String resolveForCreate(CreateDeployment deployment) {
        if (WorkloadClassifier.isModelWorkload(deployment) && nodePoolProperties.getDefaultModelPoolId() != null) {
            return nodePoolProperties.getDefaultModelPoolId();
        }
        return nodePoolProperties.getDefaultPoolId();
    }
}
