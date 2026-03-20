package com.epam.aidial.deployment.manager.web.security;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Component
@LogExecution
@ConditionalOnExpression(("'${config.rest.security.mode}' == 'oidc' OR '${config.rest.security.mode}' == 'basic'"))
public class PublicPathsResolver {

    private final boolean disableSwaggerAuthorization;

    public PublicPathsResolver(@Value("${config.rest.security.disable-swagger-authorization}") boolean disableSwaggerAuthorization) {
        this.disableSwaggerAuthorization = disableSwaggerAuthorization;
    }

    protected String[] resolvePublicPathPatterns() {
        var swaggerPaths = disableSwaggerAuthorization
                ? List.of("/swagger-ui/**", "/v3/api-docs/**")
                : List.<String>of();
        var unsecuredPaths = List.of("/api/v1/health/**", "/api/internal/**");

        return Stream.of(swaggerPaths, unsecuredPaths)
                .flatMap(Collection::stream)
                .toArray(String[]::new);
    }
}
