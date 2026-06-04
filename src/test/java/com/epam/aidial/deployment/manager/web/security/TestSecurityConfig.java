package com.epam.aidial.deployment.manager.web.security;

import com.epam.aidial.deployment.manager.utils.JwtUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class TestSecurityConfig {

    /**
     * Test implementation of {@link NimbusJwtDecoderResolver}.
     * Uses SecretKey as signature mechanism instead of production JWKS approach
     */
    @Primary
    @Bean
    public NimbusJwtDecoderResolver testNimbusJwtDecoderResolver() {
        return config -> NimbusJwtDecoder.withSecretKey(JwtUtils.hmacSecretKey()).build();
    }
}
