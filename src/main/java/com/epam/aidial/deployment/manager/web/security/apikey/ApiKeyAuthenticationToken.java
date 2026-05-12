package com.epam.aidial.deployment.manager.web.security.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String project;

    public ApiKeyAuthenticationToken(String project, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.project = project;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return project;
    }

    @Override
    public String getName() {
        return project;
    }
}
