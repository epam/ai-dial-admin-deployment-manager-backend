package com.epam.aidial.deployment.manager.utils;

import io.jsonwebtoken.Jwts;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class JwtUtils {

    public static final String SECRET_KEY = "test_secret_key_for_token_signature";

    public static SecretKey hmacSecretKey() {
        return new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public static String generateTestToken(final String audience,
                                           final String issuer,
                                           final Map<String, Object> claims) {
        final var nowMillis = System.currentTimeMillis();
        final var now = new Date(nowMillis);
        final var exp = new Date(nowMillis + 900000);

        return Jwts.builder()
            .audience().add(audience).and()
            .issuer(issuer)
            .claims().add(claims).and()
            .issuedAt(now)
            .expiration(exp)
            .signWith(hmacSecretKey(), Jwts.SIG.HS256)
            .compact();
    }
}
