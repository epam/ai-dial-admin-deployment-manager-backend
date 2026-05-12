package com.epam.aidial.deployment.manager.web.security.apikey;

import com.epam.aidial.deployment.manager.web.handler.ErrorView;
import com.epam.aidial.deployment.manager.web.security.UserSecurityDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "config.rest.security.api-key.enabled", havingValue = "true")
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final CoreApiKeyIntrospector introspector;
    private final ApiKeyCache cache;
    private final ApiKeyAuthorityResolver authorityResolver;
    private final ObjectMapper objectMapper;
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (StringUtils.isNotBlank(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(CoreApiKeyIntrospector.API_KEY_HEADER);
        if (StringUtils.isBlank(apiKey)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Authentication authentication = cache.getOrAuthenticate(apiKey, () -> authenticate(apiKey));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            chain.doFilter(request, response);
        } catch (BadCredentialsException ex) {
            SecurityContextHolder.clearContext();
            writeError(request, response, HttpStatus.UNAUTHORIZED, "Invalid API key");
        } catch (AuthenticationServiceException ex) {
            SecurityContextHolder.clearContext();
            writeError(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "API key validation is temporarily unavailable");
        }
    }

    private Authentication authenticate(String apiKey) {
        IntrospectionResult result = introspector.introspect(apiKey);
        var authorities = authorityResolver.resolve(result.rawRoles());
        var token = new ApiKeyAuthenticationToken(result.project(), authorities);
        token.setDetails(new UserSecurityDetails(null));
        if (authorities.isEmpty()) {
            log.warn("Authenticated api-key for project {} has no mapped application authorities — request will be denied",
                    result.project());
            token.setAuthenticated(false);
        }
        return token;
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpStatus status,
                            String message) throws IOException {
        ErrorView errorView = new ErrorView(request, status, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), errorView);
    }
}
