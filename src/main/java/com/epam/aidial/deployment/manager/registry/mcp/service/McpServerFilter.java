package com.epam.aidial.deployment.manager.registry.mcp.service;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.registry.mcp.model.Package;
import com.epam.aidial.deployment.manager.registry.mcp.model.RemoteTransport;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerFilterDto;
import com.epam.aidial.deployment.manager.registry.mcp.web.dto.ServerResponseDto;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@LogExecution
public class McpServerFilter {

    /**
     * Returns true if the server matches all active filter criteria.
     * Null or empty filter lists impose no constraint on that dimension.
     * Within a dimension's value list, OR logic applies. Across dimensions, AND logic applies.
     */
    public boolean matches(ServerResponseDto serverResponse, ServerFilterDto filter) {
        if (filter == null) {
            return true;
        }
        var server = serverResponse.getServer();
        if (server == null) {
            return false;
        }

        if (CollectionUtils.isNotEmpty(filter.getRemoteTypes())) {
            if (!matchesRemoteTypes(server.getRemotes(), filter.getRemoteTypes())) {
                return false;
            }
        }

        if (CollectionUtils.isNotEmpty(filter.getPackageRegistryTypes())) {
            if (!matchesPackageRegistryTypes(server.getPackages(), filter.getPackageRegistryTypes())) {
                return false;
            }
        }

        if (filter.getRepositoryExists() != null) {
            boolean hasRepository = server.getRepository() != null;
            if (filter.getRepositoryExists() != hasRepository) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if the filter has at least one active criterion.
     */
    public boolean hasActiveCriteria(ServerFilterDto filter) {
        if (filter == null) {
            return false;
        }
        return CollectionUtils.isNotEmpty(filter.getRemoteTypes())
                || CollectionUtils.isNotEmpty(filter.getPackageRegistryTypes())
                || filter.getRepositoryExists() != null;
    }

    private boolean matchesRemoteTypes(List<RemoteTransport> remotes, List<String> filterTypes) {
        if (CollectionUtils.isEmpty(remotes)) {
            return false;
        }
        return remotes.stream()
                .anyMatch(remote -> remote.getType() != null
                        && filterTypes.stream().anyMatch(ft -> ft.equalsIgnoreCase(remote.getType())));
    }

    private boolean matchesPackageRegistryTypes(List<Package> packages, List<String> filterTypes) {
        if (CollectionUtils.isEmpty(packages)) {
            return false;
        }
        return packages.stream()
                .anyMatch(pkg -> pkg.getRegistryType() != null
                        && filterTypes.stream().anyMatch(ft -> ft.equalsIgnoreCase(pkg.getRegistryType().getValue())));
    }
}
