package com.epam.aidial.deployment.manager.web.security;

import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

public interface TokenIntrospectorFactory {

    OpaqueTokenIntrospector createOpaqueTokenIntrospector();
}
