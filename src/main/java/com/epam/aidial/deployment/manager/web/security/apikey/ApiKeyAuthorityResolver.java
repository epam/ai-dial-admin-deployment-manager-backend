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

    private final UserRolesResolver delegate;

    public ApiKeyAuthorityResolver(ApiKeyProperties properties) {
        this.delegate = new UserRolesResolver(properties.getParsedRolesMapping());
    }

    public Collection<? extends GrantedAuthority> resolve(List<String> rawRoles) {
        List<SimpleGrantedAuthority> granted = rawRoles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return delegate.resolve(granted);
    }
}
