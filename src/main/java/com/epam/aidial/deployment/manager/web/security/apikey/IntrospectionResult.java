package com.epam.aidial.deployment.manager.web.security.apikey;

import java.util.List;

public record IntrospectionResult(String project, List<String> rawRoles) {
}
