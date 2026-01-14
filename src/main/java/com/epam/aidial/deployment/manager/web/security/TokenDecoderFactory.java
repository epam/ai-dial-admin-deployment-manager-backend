package com.epam.aidial.deployment.manager.web.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;

public interface TokenDecoderFactory {

    JwtDecoder createJwtDecoder();
}
