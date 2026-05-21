package com.epam.aidial.deployment.manager.web.security;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@LogExecution
@ConditionalOnProperty(value = "config.rest.security.mode", havingValue = "oidc", matchIfMissing = true)
@RequiredArgsConstructor
public class RolesMappingResolver {

    public Map<String, Set<UserRole>> resolve(Map<String, Set<UserRole>> defaultRolesMapping,
                                              Map<String, Set<UserRole>> providerRolesMapping) {
        if (MapUtils.isNotEmpty(providerRolesMapping)) {
            Map<String, Set<UserRole>> result = new HashMap<>(MapUtils.emptyIfNull(defaultRolesMapping));
            result.putAll(providerRolesMapping);
            return result;
        }

        return MapUtils.emptyIfNull(defaultRolesMapping);
    }
}
