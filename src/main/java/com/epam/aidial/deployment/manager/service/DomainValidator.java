package com.epam.aidial.deployment.manager.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DomainValidator {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,}$"
    );

    public boolean isValid(String domain) {
        if (domain == null
                || domain.contains("://")
                || domain.contains("/")
                || domain.contains("?")
                || domain.contains("#")
                || domain.contains(" ")) {
            return false;
        }
        return DOMAIN_PATTERN.matcher(domain).matches();
    }
}
