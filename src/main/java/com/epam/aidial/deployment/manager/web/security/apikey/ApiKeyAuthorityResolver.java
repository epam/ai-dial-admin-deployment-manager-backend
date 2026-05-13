package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.web.security.UserRolesResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@LogExecution
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class ApiKeyAuthorityResolver {

    private final UserRolesResolver projectKeyResolver;
    private final UserRolesResolver userClaimsResolver;

    public ApiKeyAuthorityResolver(ApiKeyProperties properties) {
        // Project-key responses (response.project present) use api-key.roles-mapping —
        // Core's project-key role names (e.g. "admin", "default") get their own mapping.
        this.projectKeyResolver = new UserRolesResolver(properties.getParsedRolesMapping());
        // JWT-rooted per-request key responses (response.userClaims present) use the OIDC
        // default.roles-mapping — the JWT roles are the same names the user would carry
        // when calling DM directly with a JWT, so the same mapping applies.
        this.userClaimsResolver = new UserRolesResolver(properties.getParsedDefaultRolesMapping());
    }

    public Collection<? extends GrantedAuthority> resolve(List<String> rawRoles, boolean fromProjectKey) {
        UserRolesResolver delegate = fromProjectKey ? projectKeyResolver : userClaimsResolver;
        List<SimpleGrantedAuthority> granted = rawRoles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return delegate.resolve(granted);
    }
}
