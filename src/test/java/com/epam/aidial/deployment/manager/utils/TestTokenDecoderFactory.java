package com.epam.aidial.deployment.manager.utils;

import com.epam.aidial.deployment.manager.web.security.IssuerToDecoderMapFactory;
import com.epam.aidial.deployment.manager.web.security.JwtProviderConfig;
import com.epam.aidial.deployment.manager.web.security.MultiIssuerJwtDecoder;
import com.epam.aidial.deployment.manager.web.security.TokenDecoderFactory;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;

import static com.epam.aidial.deployment.manager.utils.JwtUtils.SECRET_KEY;

/**
 * Test implementation of TokenDecoderFactory
 * Uses SecretKey as signature mechanism instead of production JWKS approach
 */
@Primary
@Component
public class TestTokenDecoderFactory implements TokenDecoderFactory {

    @Autowired
    private IssuerToDecoderMapFactory issuerToDecoderMapFactory;

    @Override
    public JwtDecoder createJwtDecoder() {
        final var jwtDecoder = NimbusJwtDecoder.withSecretKey(
            new SecretKeySpec(
                    SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                    SignatureAlgorithm.HS256.getValue()))
                .build();

        final var createIssuerToDecoderMap = issuerToDecoderMapFactory.createIssuerToDecoderMap(jwtDecoder,
                JwtProviderConfig.from("test", IdentityProviderTestHelper.createJwtProviderConfig()));
        return new MultiIssuerJwtDecoder(createIssuerToDecoderMap);
    }
}