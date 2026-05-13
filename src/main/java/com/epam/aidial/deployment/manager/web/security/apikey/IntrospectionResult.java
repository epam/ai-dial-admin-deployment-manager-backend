package com.epam.aidial.deployment.manager.web.security.apikey;

import java.util.List;

/**
 * Result of a Core {@code /v1/user/info} introspection.
 *
 * <p>Core returns one of two shapes depending on what minted the key:</p>
 * <ul>
 *     <li><b>Project-key auth</b> ({@code fromProjectKey == true}) — response has {@code project};
 *         {@code principal} is the project name, {@code email} is {@code null}.</li>
 *     <li><b>JWT-rooted per-request key</b> ({@code fromProjectKey == false}) — response has
 *         {@code userClaims}; {@code principal} and {@code email} are extracted from the configured
 *         OIDC default claims.</li>
 * </ul>
 */
public record IntrospectionResult(String principal, String email, List<String> rawRoles, boolean fromProjectKey) {
}
