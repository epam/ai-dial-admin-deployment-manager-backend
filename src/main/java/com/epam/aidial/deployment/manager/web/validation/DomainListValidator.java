package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.regex.Pattern;

public class DomainListValidator implements ConstraintValidator<ValidDomainList, List<String>> {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,}$"
    );

    @Override
    public boolean isValid(List<String> domains, ConstraintValidatorContext context) {
        if (domains == null) {
            return true; // Let @NotNull handle null validation
        }
        for (String domain : domains) {
            if (domain == null
                    || domain.contains("://")
                    || domain.contains("/")
                    || domain.contains("?")
                    || domain.contains("#")
                    || domain.contains(" ")) {
                return false;
            }
            if (!DOMAIN_PATTERN.matcher(domain).matches()) {
                return false;
            }
        }
        return true;
    }
}
