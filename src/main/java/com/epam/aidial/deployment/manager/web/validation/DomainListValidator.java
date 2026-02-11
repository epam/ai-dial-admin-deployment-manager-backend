package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.regex.Pattern;

public class DomainListValidator implements ConstraintValidator<ValidDomainList, List<String>> {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,}$"
    );

    private static final String ALLOW_ALL_KEY = "*";
    private static final int MIN_DOMAIN_LENGTH = 4;
    private static final int MAX_DOMAIN_LENGTH = 253;

    @Override
    public boolean isValid(List<String> domains, ConstraintValidatorContext context) {
        if (domains == null) {
            return true; // Let @NotNull handle null validation
        }
        for (String domain : domains) {
            if (domain == null) {
                return false;
            }
            if (ALLOW_ALL_KEY.equals(domain)) {
                return true;
            }
            if (domain.length() < MIN_DOMAIN_LENGTH || domain.length() > MAX_DOMAIN_LENGTH) {
                return false;
            }
            if (!DOMAIN_PATTERN.matcher(domain).matches()) {
                return false;
            }
        }
        return true;
    }
}
