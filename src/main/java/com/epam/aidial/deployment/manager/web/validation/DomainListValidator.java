package com.epam.aidial.deployment.manager.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class DomainListValidator implements ConstraintValidator<ValidDomainList, List<String>> {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,}$"
    );

    private static final String ALLOW_ALL_KEY = "*";
    private static final int MIN_DOMAIN_LENGTH = 4;
    private static final int MAX_DOMAIN_LENGTH = 253;

    public static boolean isValidDomain(String domain) {
        if (domain == null) {
            return false;
        }
        if (ALLOW_ALL_KEY.equals(domain)) {
            return true;
        }
        return domain.length() >= MIN_DOMAIN_LENGTH
                && domain.length() <= MAX_DOMAIN_LENGTH
                && DOMAIN_PATTERN.matcher(domain).matches();
    }

    @Override
    public boolean isValid(List<String> domains, ConstraintValidatorContext context) {
        if (domains == null) {
            return true; // Let @NotNull handle null validation
        }

        for (String domain : domains) {
            if (domain == null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("domain must not be null")
                        .addConstraintViolation();
                return false;
            }

            if (ALLOW_ALL_KEY.equals(domain)) {
                return true;
            }

            if (domain.length() < MIN_DOMAIN_LENGTH || domain.length() > MAX_DOMAIN_LENGTH) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        String.format("domain '%s' must be between %d and %d characters long", domain, MIN_DOMAIN_LENGTH, MAX_DOMAIN_LENGTH))
                        .addConstraintViolation();
                return false;
            }

            if (!DOMAIN_PATTERN.matcher(domain).matches()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        String.format("domain '%s' is not a valid domain name", domain))
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
