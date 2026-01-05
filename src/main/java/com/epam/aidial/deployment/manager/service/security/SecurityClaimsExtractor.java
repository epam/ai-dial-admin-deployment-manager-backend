package com.epam.aidial.deployment.manager.service.security;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@LogExecution
public class SecurityClaimsExtractor {

    @Value("${config.rest.security.email-claim}")
    private String emailClaim;

    public String getEmail() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            log.debug("Security context or authentication is null");
            return null;
        }
        Authentication authentication = context.getAuthentication();
        log.trace("Authentication: {}", authentication);
        if (context.getAuthentication() instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt token = jwtAuthenticationToken.getToken();
            log.trace("token claims: {}", token.getClaims());
            Object uniqueName = token.getClaims().get(emailClaim);
            if (uniqueName != null) {
                return Objects.toString(uniqueName);
            }
        }
        return null;
    }
}
