package com.epam.aidial.deployment.manager.web.security;

import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public interface NimbusJwtDecoderResolver {

    NimbusJwtDecoder resolve(JwtProviderConfig config);
}
