package com.epam.aidial.deployment.manager.service.nodepool;

import com.epam.aidial.deployment.manager.configuration.NodePoolProperties;
import com.epam.aidial.deployment.manager.configuration.NodePoolProperties.PoolConfig;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
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

    public List<PoolConfig> getNodePools() {
        var pools = nodePoolProperties.getPools();
        if (CollectionUtils.isEmpty(pools)) {
            return List.of();
        }
        return List.copyOf(pools);
    }

    public NodePoolProperties getProperties() {
        return nodePoolProperties;
    }
}
