package com.epam.aidial.deployment.manager.service.security;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.web.security.UserSecurityDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LogExecution
public class SecurityClaimsExtractor {

    public String getEmail() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context == null || context.getAuthentication() == null) {
            log.debug("Security context or authentication is null");
            return null;
        }
        Authentication authentication = context.getAuthentication();
        log.trace("Authentication: {}", authentication);

        if (authentication.getDetails() instanceof UserSecurityDetails(String email)) {
            return email;
        }
        return null;
    }
}
